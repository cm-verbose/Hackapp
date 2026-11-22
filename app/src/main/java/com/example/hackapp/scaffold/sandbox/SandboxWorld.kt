package com.example.hackapp.scaffold.sandbox

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.unit.Density
import com.example.hackapp.scaffold.sandbox.share.NearbyShare
import com.example.hackapp.scaffold.sandbox.share.ShareMessage
import com.example.hackapp.scaffold.sandbox.share.TargetCandidate
import com.example.hackapp.scaffold.sandbox.share.ThrowGeometry
import com.example.hackapp.scaffold.sandbox.share.ThrowTargeting
import com.example.hackapp.scaffold.sandbox.shape.PhysicalShape
import com.example.hackapp.scaffold.sandbox.shape.toPixels
import com.example.hackapp.scaffold.sandbox.simulatedworld.createBoundaryWalls
import com.example.hackapp.scaffold.sandbox.simulatedworld.createPhysicalShape
import org.jbox2d.collision.AABB
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.World

/** Minimum speed (m/s) for a released shape to leave the screen instead of bouncing. */
private const val THROW_MIN_SPEED = 10f

/** How long after release a shape can still be thrown, in simulated seconds. */
private const val THROW_WINDOW_SECONDS = 0.6f

/** How long a thrown shape takes to reappear on the device it was thrown from. */
private const val RESPAWN_DELAY_NANOS = 3_000_000_000L

/** A thrown shape is removed after this long even if it hasn't left the screen yet. */
private const val LEAVE_TIMEOUT_NANOS = 1_500_000_000L

/** Received shapes beyond this count push out the oldest received one. */
private const val MAX_RECEIVED_ITEMS = 8

/** Speed range (m/s) received shapes enter the screen with. */
private const val MIN_RECEIVED_SPEED = 6f
private const val MAX_RECEIVED_SPEED = 25f

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
    private var throwCandidateAge = 0f

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

    /** Call when the user lets go of [shape]: a fast release may throw it to another device. */
    fun onReleased(shape: PhysicalShape?) {
        throwCandidate = items.firstOrNull { it.physicalShape === shape }
        throwCandidateAge = 0f
    }

    /**
     * Call before each world step. If the freshly released shape is fast and about to hit a
     * wall, and a peer lies in that direction, the shape is sent there and flies off screen.
     */
    fun checkThrow(dt: Float, nowNanos: Long, share: NearbyShare?, rotation: FloatArray?) {
        val item = throwCandidate ?: return
        throwCandidateAge += dt
        if (throwCandidateAge > THROW_WINDOW_SECONDS || item !in items || share == null) {
            throwCandidate = null
            return
        }
        val body = item.physicalShape.body
        val v = body.linearVelocity
        val speed = v.length()
        if (speed < THROW_MIN_SPEED || !willHitWall(body, v, dt * 2f)) return

        // Only the first wall hit counts, so a bounce never triggers a throw elsewhere.
        throwCandidate = null
        val bearing = rotation?.let { ThrowGeometry.screenToBearing(it, v.x, v.y) }
        val candidates = share.peers.values.map { peer ->
            val uwbBearing = if (peer.hasFreshUwb && rotation != null) {
                ThrowGeometry.uwbToBearing(rotation, peer.uwbAzimuthDeg!!, peer.uwbElevationDeg)
            } else {
                null
            }
            TargetCandidate(peer.endpointId, peer.forwardBearing, uwbBearing)
        }
        val target = ThrowTargeting.pickTarget(bearing, candidates) ?: return

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
    fun receive(message: ShareMessage.Throw, rotation: FloatArray?, heldShape: PhysicalShape?) {
        val index = message.itemIndex
        if (index !in specs.indices) return

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

    /** Whether [body]'s bounds, moved by [velocity] for [seconds], cross a screen edge. */
    private fun willHitWall(body: Body, velocity: Vec2, seconds: Float): Boolean {
        val box = body.boundsPx() ?: return false
        val mx = velocity.x.toPixels() * seconds
        val my = velocity.y.toPixels() * seconds
        return (velocity.x < 0f && box.lowerBound.x + mx <= 0f) ||
            (velocity.x > 0f && box.upperBound.x + mx >= widthPx) ||
            (velocity.y < 0f && box.lowerBound.y + my <= 0f) ||
            (velocity.y > 0f && box.upperBound.y + my >= heightPx)
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
