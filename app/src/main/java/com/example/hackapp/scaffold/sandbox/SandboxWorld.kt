package com.example.hackapp.scaffold.sandbox

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import com.example.hackapp.scaffold.sandbox.share.ShareMessage
import com.example.hackapp.scaffold.sandbox.share.TargetCandidate
import com.example.hackapp.scaffold.sandbox.share.ThrowChannel
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
import kotlin.math.floor

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

/** Gap between items in the starting grid, in pixels per density unit. */
private const val GRID_GAP_DP = 8f

/** How many items of [itemPx] fit in the starting grid of a [widthPx] by [heightPx] area. */
fun sandboxCapacity(widthPx: Float, heightPx: Float, itemPx: Float, density: Float): Int {
    val cell = itemPx + GRID_GAP_DP * density
    if (cell <= 0f) return 0
    return floor(widthPx / cell).toInt().coerceAtLeast(0) * floor(heightPx / cell).toInt().coerceAtLeast(0)
}

/** What happened when a fast shape was released. */
sealed interface ThrowOutcome {
    val item: CreatedPhysicsItem

    /** Sent to the peer named [peerName]; the shape flies off screen. */
    data class Sent(override val item: CreatedPhysicsItem, val peerName: String?) : ThrowOutcome

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
    spec: SandboxItem,
    val physicalShape: PhysicalShape,
    /** `true` for this device's own shapes, which reappear after being thrown away. */
    val isOwn: Boolean,
    /** Whether the shape pops in with a scale animation when it appears. */
    val popIn: Boolean,
) {
    /** Replaced when the item's content changes, without touching its body. */
    var spec by mutableStateOf(spec)
        internal set

    /** Frame time the shape was thrown to another device, or `null` if it is staying. */
    var leavingSinceNanos: Long? = null
        internal set

    val isLeaving: Boolean get() = leavingSinceNanos != null
}

/**
 * The Box2D world plus everything that adds shapes to it or takes them away: the starting grid,
 * list updates, throws to nearby devices, shapes received from them and respawns.
 *
 * Must be used from the main thread, and never while [world] is stepping.
 */
class SandboxWorld(
    private val widthPx: Float,
    private val heightPx: Float,
    private val density: Density,
) {
    val world = World(Vec2(0f, 9.8f)).also { it.createBoundaryWalls(widthPx, heightPx, density) }

    /** Shapes currently in the world, in drawing order. */
    val items = mutableStateListOf<CreatedPhysicsItem>()

    private var specs: List<SandboxItem> = emptyList()
    private var nextId = 0L
    private var linearDamping = 0f
    private var angularDamping = 0f

    /** Own item key → frame time at which it reappears. */
    private val pendingRespawns = mutableMapOf<Any, Long>()

    /** The most recently released shape, which may be thrown while it's fresh. */
    private var throwCandidate: CreatedPhysicsItem? = null

    fun populate(initial: List<SandboxItem>, linearDamping: Float, angularDamping: Float) {
        this.linearDamping = linearDamping
        this.angularDamping = angularDamping
        applySpecs(initial, popIn = false)
    }

    /**
     * Matches the world to [newSpecs] by key: existing items keep their body and position, new
     * ones pop in at a free spot, and items no longer listed are removed. Release a held shape
     * first if it is going away ([isListed]).
     */
    fun sync(newSpecs: List<SandboxItem>) = applySpecs(newSpecs, popIn = true)

    /** Whether [shape] is an own item still in [newSpecs], or not an own item at all. */
    fun isListed(shape: PhysicalShape, newSpecs: List<SandboxItem>): Boolean {
        val item = items.firstOrNull { it.physicalShape === shape } ?: return false
        return !item.isOwn || newSpecs.any { it.key == item.spec.key }
    }

    private fun applySpecs(newSpecs: List<SandboxItem>, popIn: Boolean) {
        val byKey = newSpecs.associateBy { it.key }
        items.filter { it.isOwn && it.spec.key !in byKey }.forEach(::remove)
        pendingRespawns.keys.retainAll(byKey.keys)
        specs = newSpecs
        val present = HashSet<Any>(pendingRespawns.keys)
        items.filter { it.isOwn }.forEach { item ->
            byKey[item.spec.key]?.let { item.spec = it }
            present += item.spec.key
        }
        newSpecs.filter { it.key !in present }.forEach { spec ->
            spawn(spec, freeSlot(spec), isOwn = true, popIn = popIn)
        }
    }

    /** The first grid cell with no shape on it, or the screen center when the grid is full. */
    private fun freeSlot(spec: SandboxItem): Pair<Float, Float> {
        val cell = with(density) { maxOf(spec.width, spec.height).toPx() } + GRID_GAP_DP * density.density
        val cols = floor(widthPx / cell).toInt().coerceAtLeast(1)
        val rows = floor(heightPx / cell).toInt().coerceAtLeast(1)
        val left = (widthPx - cols * cell) / 2f
        val top = (heightPx - rows * cell) / 2f
        for (index in 0 until cols * rows) {
            val x = left + (index % cols + 0.5f) * cell
            val y = top + (index / cols + 0.5f) * cell
            val taken = items.any {
                val dx = it.physicalShape.body.position.x.toPixels() - x
                val dy = it.physicalShape.body.position.y.toPixels() - y
                dx * dx + dy * dy < cell * cell * 0.8f
            }
            if (!taken) return x to y
        }
        return widthPx / 2f to heightPx / 2f
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

    /** True when nothing is moving and no shape is due back, so frames can be skipped. */
    fun isAtRest(): Boolean = pendingRespawns.isEmpty() && throwCandidate == null &&
        items.none { it.physicalShape.body.isAwake }

    /** Shapes the user may pick up. */
    fun grabbableShapes(): List<PhysicalShape> =
        items.filter { !it.isLeaving }.map { it.physicalShape }

    /** The top-most item under [positionPx], for taps. */
    fun itemAt(positionPx: Offset): CreatedPhysicsItem? {
        val point = Vec2(positionPx.x.toMeters(), positionPx.y.toMeters())
        return items.lastOrNull { item ->
            if (item.isLeaving) return@lastOrNull false
            var fixture = item.physicalShape.body.fixtureList
            while (fixture != null) {
                if (fixture.testPoint(point)) return@lastOrNull true
                fixture = fixture.next
            }
            false
        }
    }

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
        share: ThrowChannel?,
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
        val itemIndex = specs.indexOfFirst { it.key == item.spec.key }
        if (itemIndex < 0) return null
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
                itemIndex = itemIndex,
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
        return ThrowOutcome.Sent(item, share.peers[target]?.name)
    }

    /** Call after each frame: removes shapes that left and brings own shapes back. */
    fun update(nowNanos: Long) {
        items.filter { it.isLeaving }.forEach { item ->
            val gone = isOffScreen(item.physicalShape.body) ||
                nowNanos - item.leavingSinceNanos!! > LEAVE_TIMEOUT_NANOS
            if (gone) {
                remove(item)
                if (item.isOwn) pendingRespawns[item.spec.key] = nowNanos + RESPAWN_DELAY_NANOS
            }
        }

        val due = pendingRespawns.filterValues { it <= nowNanos }.keys
        if (due.isNotEmpty()) {
            pendingRespawns.keys.removeAll(due)
            specs.filter { it.key in due }.forEach { spawn(it, freeSlot(it), isOwn = true, popIn = true) }
        }
    }

    /**
     * Adds a shape thrown from another device, entering from the side it was thrown from.
     * @return the received item's spec, or `null` if it isn't in this device's item list.
     */
    fun receive(
        message: ShareMessage.Throw,
        rotation: FloatArray?,
        heldShape: PhysicalShape?,
    ): SandboxItem? {
        val spec = specs.getOrNull(message.itemIndex) ?: return null

        // Without a compass on either side, shapes drop in from the top.
        val (dx, dy) = message.bearing
            ?.let { bearing -> rotation?.let { ThrowGeometry.bearingToScreen(it, bearing) } }
            ?: (0f to 1f)
        val inset = with(density) { maxOf(spec.width, spec.height).toPx() } / 2f + 4f
        val position = ThrowGeometry.entryPoint(dx, dy, widthPx, heightPx, inset)
        val speed = message.speed.coerceIn(MIN_RECEIVED_SPEED, MAX_RECEIVED_SPEED)

        spawn(
            spec = spec,
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
        spec: SandboxItem,
        positionPx: Pair<Float, Float>,
        isOwn: Boolean,
        popIn: Boolean,
        velocity: Vec2? = null,
        angularVelocity: Float = 0f,
    ) {
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
        items += CreatedPhysicsItem(nextId++, spec, shape, isOwn, popIn)
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
