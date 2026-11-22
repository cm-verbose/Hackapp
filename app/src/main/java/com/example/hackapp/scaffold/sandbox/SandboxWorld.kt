package com.example.hackapp.scaffold.sandbox

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import com.example.hackapp.scaffold.sandbox.share.NearbyShare
import com.example.hackapp.scaffold.sandbox.share.ShareMessage
import com.example.hackapp.scaffold.sandbox.share.TargetCandidate
import com.example.hackapp.scaffold.sandbox.share.ThrowGeometry
import com.example.hackapp.scaffold.sandbox.share.ThrowTargeting
import com.example.hackapp.scaffold.sandbox.shape.PhysicalShape
import com.example.hackapp.scaffold.sandbox.shape.toMeters
import com.example.hackapp.scaffold.sandbox.shape.toPixels
import com.example.hackapp.scaffold.sandbox.simulatedworld.createBoundaryWalls
import com.example.hackapp.scaffold.sandbox.simulatedworld.createPhysicalShape
import org.jbox2d.collision.AABB
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.World

private const val TAG = "SandboxWorld"

/** Minimum speed (m/s) for a released shape to leave the screen instead of bouncing. */
private const val THROW_MIN_SPEED = 10f

/** Cap (m/s) on the speed a flick gives a shape, so it can't tunnel through other shapes. */
private const val MAX_FLICK_SPEED = 40f


/** How long a thrown shape takes to reappear on the device it was thrown from. */
private const val RESPAWN_DELAY_NANOS = 3_000_000_000L

/** A thrown shape is removed after this long even if it hasn't left the screen yet. */
private const val LEAVE_TIMEOUT_NANOS = 1_500_000_000L

/** Received shapes beyond this count push out the oldest received one. */
private const val MAX_RECEIVED_ITEMS = 8

/** Speed range (m/s) received shapes enter the screen with. */
private const val MIN_RECEIVED_SPEED = 6f
private const val MAX_RECEIVED_SPEED = 25f

/** What happened when a fast shape hit a wall right after being released. */
sealed interface ThrowOutcome {
    val item: CreatedPhysicsItem

    /** Sent to the peer named [peerName]; the shape flies off screen. */
    data class Sent(override val item: CreatedPhysicsItem, val peerName: String) : ThrowOutcome

    /** Nearby sharing isn't running (feature off or permissions denied); the shape bounced. */
    data class SharingOff(override val item: CreatedPhysicsItem) : ThrowOutcome

    /** No device is connected; the shape bounced. */
    data class NoPeers(override val item: CreatedPhysicsItem) : ThrowOutcome

    /** Several devices are connected but none lies in that direction; the shape bounced. */
    data class NoTargetInDirection(override val item: CreatedPhysicsItem) : ThrowOutcome
}

/** A shape in the simulation. */
class CreatedPhysicsItem(
    /** Unique per world, used as the composition key. */
    val id: Long,
    /** Index in the item list; identifies the shape across devices. */
    val specIndex: Int,
    val spec: SandboxItem,
    val physicalShape: PhysicalShape,
    /** `true` for this device's own shapes, which reappear after being thrown away. */
    val isOwn: Boolean,
    /** Whether the shape pops in with a scale animation when it appears. */
    val popIn: Boolean,
) {
    /** Frame time the shape was thrown to another device, or `null` if it is staying. */
    var leavingSinceNanos: Long? = null
        internal set

    val isLeaving: Boolean get() = leavingSinceNanos != null
}

/**
 * The Box2D world plus everything that adds shapes to it or takes them away: the initial
 * layout, throws to nearby devices, shapes received from them and respawns.
 *
 * Must be used from the main thread, and never while [world] is stepping.
 */
class SandboxWorld(
    private val specs: List<SandboxItem>,
    private val widthPx: Float,
    private val heightPx: Float,
    private val density: Density,
) {
    val world = World(Vec2(0f, 9.8f)).also { it.createBoundaryWalls(widthPx, heightPx, density) }

    /** Shapes currently in the world, in drawing order. */
    val items = mutableStateListOf<CreatedPhysicsItem>()

    private var nextId = 0L
    private var linearDamping = 0f
    private var angularDamping = 0f

    /** Spec index → frame time at which it reappears. */
    private val pendingRespawns = mutableListOf<Pair<Int, Long>>()

    /** The most recently released shape, which may be thrown while it's fresh. */
    private var throwCandidate: CreatedPhysicsItem? = null

    /** Where each own shape starts and reappears, in pixels. */
    private val homes: List<Pair<Float, Float>> = specs.mapIndexed { index, spec ->
        val cols = 2
        val wPx = with(density) { spec.width.toPx() }
        val hPx = with(density) { spec.height.toPx() }
        val col = index % cols
        val row = index / cols
        val posX = spec.initialX?.let { with(density) { it.toPx() } }
            ?: (widthPx / 2f + (col - 0.5f) * (wPx + 80f))
        val posY = spec.initialY?.let { with(density) { it.toPx() } }
            ?: (heightPx / 2f + (row - 1f) * (hPx + 80f))
        posX to posY
    }

    fun populate(linearDamping: Float, angularDamping: Float) {
        this.linearDamping = linearDamping
        this.angularDamping = angularDamping
        specs.indices.forEach { spawn(it, homes[it], isOwn = true, popIn = false) }
    }

    fun setDamping(linear: Float, angular: Float) {
        linearDamping = linear
        angularDamping = angular
        items.forEach {
            it.physicalShape.body.linearDamping = linear
            it.physicalShape.body.angularDamping = angular
        }
    }

    fun wakeAll() = items.forEach { it.physicalShape.body.isAwake = true }

    /** Shapes the user may pick up. */
    fun grabbableShapes(): List<PhysicalShape> =
        items.filter { !it.isLeaving }.map { it.physicalShape }

    /** Whether a shape released with this finger velocity (pixels per second) would be thrown. */
    fun isThrowSpeed(fingerVelocityPx: Offset): Boolean =
        fingerVelocityPx.getDistance().toMeters() >= THROW_MIN_SPEED

    /**
     * Call when the user lets go of [shape], with the finger's velocity in pixels per second.
     *
     * The shape leaves with the finger's velocity, not its own: a shape dragged into a wall
     * or another shape bounces while held, so at release it can be moving the opposite way to
     * the flick, which would throw it to the wrong device. A fast enough release may then be
     * thrown to another device by [checkThrow].
     */
    fun onReleased(shape: PhysicalShape?, fingerVelocityPx: Offset) {
        throwCandidate = items.firstOrNull { it.physicalShape === shape }
        val body = shape?.body ?: return
        val finger = Vec2(fingerVelocityPx.x.toMeters(), fingerVelocityPx.y.toMeters())
        val speed = finger.length()
        body.linearVelocity = if (speed > MAX_FLICK_SPEED) finger.mul(MAX_FLICK_SPEED / speed) else finger
        Log.d(TAG, "Released at ${body.linearVelocity.length()} m/s (throw needs $THROW_MIN_SPEED)")
    }

    /**
     * Call before each world step. A shape released at [THROW_MIN_SPEED] or faster is thrown
     * right away: if a peer lies in that direction, it is sent there and flies off screen,
     * passing through the other shapes.
     *
     * The throw is decided at release rather than when the shape reaches a wall, because with
     * several large shapes on screen a flick almost always hits another shape first.
     *
     * @return what happened, or `null` if the release was too slow to be a throw.
     */
    fun checkThrow(
        nowNanos: Long,
        share: NearbyShare?,
        rotation: FloatArray?,
    ): ThrowOutcome? {
        val item = throwCandidate ?: return null
        throwCandidate = null
        if (item !in items) return null
        val body = item.physicalShape.body
        val v = body.linearVelocity
        val speed = v.length()
        if (speed < THROW_MIN_SPEED) return null

        if (share == null) return ThrowOutcome.SharingOff(item)
        if (share.peers.isEmpty()) return ThrowOutcome.NoPeers(item)
        val bearing = rotation?.let { ThrowGeometry.screenToBearing(it, v.x, v.y) }
        val candidates = share.peers.values.map { peer ->
            val uwbBearing = if (peer.hasFreshUwb && rotation != null) {
                ThrowGeometry.uwbToBearing(rotation, peer.uwbAzimuthDeg!!, peer.uwbElevationDeg)
            } else {
                null
            }
            TargetCandidate(peer.endpointId, peer.forwardBearing, uwbBearing)
        }
        val target = ThrowTargeting.pickTarget(bearing, candidates)
        Log.d(TAG, "Throw at $speed m/s, bearing=$bearing, candidates=$candidates -> target=$target")
        if (target == null) return ThrowOutcome.NoTargetInDirection(item)

        share.sendThrow(
            target,
            ShareMessage.Throw(
                itemIndex = item.specIndex,
                bearing = bearing,
                speed = speed,
                angularVelocity = body.angularVelocity,
            ),
        )
        // Let it pass through the walls and fly straight off screen.
        var fixture = body.fixtureList
        while (fixture != null) {
            fixture.filterData = fixture.filterData.apply { maskBits = 0 }
            fixture = fixture.next
        }
        body.gravityScale = 0f
        body.linearDamping = 0f
        item.leavingSinceNanos = nowNanos
        return ThrowOutcome.Sent(item, share.peers[target]?.name ?: "a nearby device")
    }

    /** Call after each frame: removes shapes that left and brings own shapes back. */
    fun update(nowNanos: Long) {
        items.filter { it.isLeaving }.forEach { item ->
            val gone = isOffScreen(item.physicalShape.body) ||
                nowNanos - item.leavingSinceNanos!! > LEAVE_TIMEOUT_NANOS
            if (gone) {
                remove(item)
                if (item.isOwn) pendingRespawns += item.specIndex to nowNanos + RESPAWN_DELAY_NANOS
            }
        }

        val due = pendingRespawns.filter { it.second <= nowNanos }
        if (due.isNotEmpty()) {
            pendingRespawns.removeAll(due)
            due.forEach { (index, _) -> spawn(index, homes[index], isOwn = true, popIn = true) }
        }
    }

    /** Adds a shape thrown from another device, entering from the side it was thrown from. */
    /** @return the received item's spec, or `null` if it isn't in this device's item list. */
    fun receive(
        message: ShareMessage.Throw,
        rotation: FloatArray?,
        heldShape: PhysicalShape?,
    ): SandboxItem? {
        val index = message.itemIndex
        if (index !in specs.indices) return null

        // Without a compass on either side, shapes drop in from the top.
        val (dx, dy) = message.bearing
            ?.let { bearing -> rotation?.let { ThrowGeometry.bearingToScreen(it, bearing) } }
            ?: (0f to 1f)
        val spec = specs[index]
        val inset = with(density) { maxOf(spec.width, spec.height).toPx() } / 2f + 4f
        val position = ThrowGeometry.entryPoint(dx, dy, widthPx, heightPx, inset)
        val speed = message.speed.coerceIn(MIN_RECEIVED_SPEED, MAX_RECEIVED_SPEED)

        spawn(
            index = index,
            positionPx = position,
            isOwn = false,
            popIn = false,
            velocity = Vec2(dx * speed, dy * speed),
            angularVelocity = message.angularVelocity,
        )

        val received = items.filter { !it.isOwn && !it.isLeaving && it.physicalShape !== heldShape }
        received.take((received.size - MAX_RECEIVED_ITEMS).coerceAtLeast(0)).forEach(::remove)
        return spec
    }

    private fun spawn(
        index: Int,
        positionPx: Pair<Float, Float>,
        isOwn: Boolean,
        popIn: Boolean,
        velocity: Vec2? = null,
        angularVelocity: Float = 0f,
    ) {
        val spec = specs[index]
        val shape = world.createPhysicalShape(
            shape = spec.shape,
            density = density,
            dimensionsPixels = with(density) { spec.width.toPx() to spec.height.toPx() },
            initialPositionPixels = positionPx,
            linearDamping = linearDamping,
            angularDamping = angularDamping,
            restitution = spec.restitution,
            friction = 0.02f, // Low friction prevents shapes sticking to each other or walls
        )
        velocity?.let { shape.body.linearVelocity = it }
        shape.body.angularVelocity = angularVelocity
        items += CreatedPhysicsItem(nextId++, index, spec, shape, isOwn, popIn)
    }

    private fun remove(item: CreatedPhysicsItem) {
        items.remove(item)
        world.destroyBody(item.physicalShape.body)
        if (throwCandidate === item) throwCandidate = null
    }


    private fun isOffScreen(body: Body): Boolean {
        val box = body.boundsPx() ?: return true
        return box.upperBound.x < 0f || box.lowerBound.x > widthPx ||
            box.upperBound.y < 0f || box.lowerBound.y > heightPx
    }

    /** Union of the body's fixture bounds in pixels, or `null` without fixtures. */
    private fun Body.boundsPx(): AABB? {
        var fixture = fixtureList ?: return null
        val box = AABB(fixture.getAABB(0))
        while (true) {
            fixture = fixture.next ?: break
            box.combine(fixture.getAABB(0))
        }
        box.lowerBound.set(box.lowerBound.x.toPixels(), box.lowerBound.y.toPixels())
        box.upperBound.set(box.upperBound.x.toPixels(), box.upperBound.y.toPixels())
        return box
    }
}
