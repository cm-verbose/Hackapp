package com.example.hackapp.scaffold.sandbox

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.collision.shapes.Shape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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

/** Gap kept between items when they are placed, in dp. */
private const val PLACEMENT_GAP_DP = 2f

/** Distance between the spots tried when placing an item, in dp. */
private const val PLACEMENT_STEP_DP = 8f

/** The screen box of a placed item, by its center and half size. */
internal data class PlacedBox(val x: Float, val y: Float, val halfWidth: Float, val halfHeight: Float)

/**
 * The first free spot for an item of this size, from the bottom row up and left to right, clear of
 * [placed] and of the [keepOutPx] square in the bottom-end corner. Null when none is left.
 */
internal fun firstFreeSpot(
    itemWidth: Float,
    itemHeight: Float,
    placed: List<PlacedBox>,
    widthPx: Float,
    heightPx: Float,
    density: Float,
    keepOutPx: Float = 0f,
): PlacedBox? {
    val gap = PLACEMENT_GAP_DP * density
    val step = PLACEMENT_STEP_DP * density
    val halfW = itemWidth / 2f
    val halfH = itemHeight / 2f
    var y = heightPx - halfH - gap
    while (y >= halfH + gap) {
        var x = halfW + gap
        while (x <= widthPx - halfW - gap) {
            if (keepOutPx > 0f && x + halfW + gap > widthPx - keepOutPx && y + halfH + gap > heightPx - keepOutPx) break
            val blocker = placed
                .filter { abs(it.x - x) < it.halfWidth + halfW + gap && abs(it.y - y) < it.halfHeight + halfH + gap }
                .maxByOrNull { it.x + it.halfWidth }
                ?: return PlacedBox(x, y, halfW, halfH)
            // Jump past the blocker, which keeps packing tight and the search fast.
            x = blocker.x + blocker.halfWidth + halfW + gap + 0.5f
        }
        y -= step
    }
    return null
}

/** The spot overlapping [placed] the least, for when no free spot is left. */
internal fun leastCrowdedSpot(
    itemWidth: Float,
    itemHeight: Float,
    placed: List<PlacedBox>,
    widthPx: Float,
    heightPx: Float,
    density: Float,
    keepOutPx: Float = 0f,
): PlacedBox {
    val step = PLACEMENT_STEP_DP * density
    val halfW = itemWidth / 2f
    val halfH = itemHeight / 2f
    var best = PlacedBox(widthPx / 2f, halfH, halfW, halfH)
    var bestOverlap = Float.MAX_VALUE
    var y = halfH
    while (y <= heightPx - halfH) {
        var x = halfW
        while (x <= widthPx - halfW) {
            if (keepOutPx <= 0f || x + halfW <= widthPx - keepOutPx || y + halfH <= heightPx - keepOutPx) {
                val overlap = placed.sumOf { box ->
                    val dx = box.halfWidth + halfW - abs(box.x - x)
                    val dy = box.halfHeight + halfH - abs(box.y - y)
                    if (dx > 0f && dy > 0f) (dx * dy).toDouble() else 0.0
                }.toFloat()
                if (overlap < bestOverlap) {
                    bestOverlap = overlap
                    best = PlacedBox(x, y, halfW, halfH)
                }
            }
            x += step
        }
        y += step
    }
    return best
}

/** How many of [sizesPx], in order, fit the area without touching. Stops at the first that does not. */
fun sandboxFitCount(
    sizesPx: List<Pair<Float, Float>>,
    widthPx: Float,
    heightPx: Float,
    density: Float,
    keepOutPx: Float = 0f,
): Int {
    val placed = ArrayList<PlacedBox>()
    for ((itemWidth, itemHeight) in sizesPx) {
        placed += firstFreeSpot(itemWidth, itemHeight, placed, widthPx, heightPx, density, keepOutPx) ?: break
    }
    return placed.size
}

/** The smallest size items shrink to as a collection grows. */
const val MIN_ITEM_SCALE = 0.5f

/** Scales are tried in steps of 1/[SCALE_DIVISIONS], so a small change in the list cannot jitter. */
private const val SCALE_DIVISIONS = 20

/** How much the item scale may change in one physics step. About 0.4 s from full size to half. */
private const val SCALE_PER_STEP = 0.02f

/** One scale for every item, and how many of the list, from its start, are shown at that scale. */
data class SandboxLayout(val scale: Float, val count: Int)

private fun fitsAll(
    sizesPx: List<Pair<Float, Float>>,
    scale: Float,
    widthPx: Float,
    heightPx: Float,
    density: Float,
    keepOutPx: Float,
): Boolean {
    val area = sizesPx.sumOf { (w, h) -> (w * scale * h * scale).toDouble() }
    if (area > widthPx * heightPx) return false
    // Largest first packs best, and makes the result independent of the list order.
    val scaled = sizesPx.map { (w, h) -> w * scale to h * scale }
        .sortedWith(compareByDescending<Pair<Float, Float>> { it.first * it.second }.thenByDescending { it.first })
    return sandboxFitCount(scaled, widthPx, heightPx, density, keepOutPx) == scaled.size
}

/**
 * The largest scale, from 1 down to [MIN_ITEM_SCALE], at which every item fits. [sizesPx] is
 * newest first. When even the smallest scale cannot hold them all, it keeps that scale and as many
 * of the newest items as fit.
 */
fun sandboxLayout(
    sizesPx: List<Pair<Float, Float>>,
    widthPx: Float,
    heightPx: Float,
    density: Float,
    keepOutPx: Float = 0f,
): SandboxLayout {
    if (sizesPx.isEmpty()) return SandboxLayout(1f, 0)
    val lastStep = ((1f - MIN_ITEM_SCALE) * SCALE_DIVISIONS).toInt()
    for (step in 0..lastStep) {
        val scale = (SCALE_DIVISIONS - step) / SCALE_DIVISIONS.toFloat()
        // Every older part of the list must fit too, so earning an item can never enlarge the others.
        val fits = (sizesPx.size downTo 1).all { fitsAll(sizesPx.takeLast(it), scale, widthPx, heightPx, density, keepOutPx) }
        if (fits) return SandboxLayout(scale, sizesPx.size)
    }
    if (fitsAll(sizesPx, MIN_ITEM_SCALE, widthPx, heightPx, density, keepOutPx)) {
        return SandboxLayout(MIN_ITEM_SCALE, sizesPx.size)
    }
    var fits = 0
    var tooMany = sizesPx.size
    while (tooMany - fits > 1) {
        val middle = (fits + tooMany) / 2
        if (fitsAll(sizesPx.take(middle), MIN_ITEM_SCALE, widthPx, heightPx, density, keepOutPx)) fits = middle else tooMany = middle
    }
    return SandboxLayout(MIN_ITEM_SCALE, fits)
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

    /** The full-size collision shape, so resizing never re-reads the outline. */
    internal val baseShape: Shape? = physicalShape.body.fixtureList?.shape?.clone()
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
    /** Side of a solid square in the bottom-end corner, so no shape can rest under a control. */
    private val keepOutPx: Float = 0f,
) {
    val world = World(Vec2(0f, 9.8f)).also {
        it.createBoundaryWalls(widthPx, heightPx, density)
        if (keepOutPx > 0f) it.createKeepOut()
    }

    /** Shapes currently in the world, in drawing order. */
    val items = mutableStateListOf<CreatedPhysicsItem>()

    private var specs: List<SandboxItem> = emptyList()
    private val itemScaleState = mutableFloatStateOf(1f)
    private var targetScale = 1f

    /** The size of every item right now, as a fraction of its full size. */
    val itemScale: Float get() = itemScaleState.floatValue

    private var nextId = 0L
    private var linearDamping = 0f
    private var angularDamping = 0f

    /** Own item key → frame time at which it reappears. */
    private val pendingRespawns = mutableMapOf<Any, Long>()

    /** The most recently released shape, which may be thrown while it's fresh. */
    private var throwCandidate: CreatedPhysicsItem? = null

    fun populate(initial: List<SandboxItem>, linearDamping: Float, angularDamping: Float, scale: Float = 1f) {
        itemScaleState.floatValue = scale
        targetScale = scale
        this.linearDamping = linearDamping
        this.angularDamping = angularDamping
        applySpecs(initial, popIn = false)
    }

    /**
     * Matches the world to [newSpecs] by key: existing items keep their body and position, new
     * ones pop in at a free spot, and items no longer listed are removed. Release a held shape
     * first if it is going away ([isListed]). A new [scale] is reached gradually by [stepScale],
     * and new items wait for it, so they land in space that is really free.
     */
    fun sync(newSpecs: List<SandboxItem>, scale: Float = targetScale) {
        targetScale = scale
        applySpecs(newSpecs, popIn = true)
    }

    /** Call before each world step: moves every body one small step toward the target scale. */
    fun stepScale() {
        val current = itemScale
        if (current == targetScale) return
        val growing = targetScale > current
        val next = if (abs(targetScale - current) <= SCALE_PER_STEP) {
            targetScale
        } else {
            current + if (growing) SCALE_PER_STEP else -SCALE_PER_STEP
        }
        itemScaleState.floatValue = next
        items.forEach { item ->
            applyScale(item, next)
            if (growing && !item.isLeaving) keepInside(item)
            item.physicalShape.body.isAwake = true
        }
        if (next == targetScale) spawnMissing(popIn = true)
    }

    /** Swaps the body's collision shape for a scaled copy, keeping position and velocity. */
    private fun applyScale(item: CreatedPhysicsItem, scale: Float) {
        val base = item.baseShape ?: return
        val body = item.physicalShape.body
        val old = body.fixtureList ?: return
        val def = FixtureDef().apply {
            shape = scaledShape(base, scale)
            density = old.density
            friction = old.friction
            restitution = old.restitution
            filter.set(old.filterData)
        }
        body.destroyFixture(old)
        body.createFixture(def)
    }

    private fun scaledShape(base: Shape, scale: Float): Shape = when (base) {
        is CircleShape -> CircleShape().apply {
            m_radius = base.m_radius * scale
            m_p.set(base.m_p).mulLocal(scale)
        }
        is PolygonShape -> PolygonShape().apply {
            val count = base.vertexCount
            set(Array(count) { base.getVertex(it).mul(scale) }, count)
        }
        else -> base
    }

    private fun boxOf(item: CreatedPhysicsItem): PlacedBox {
        val shape = item.physicalShape
        val c = abs(cos(shape.body.angle))
        val s = abs(sin(shape.body.angle))
        return PlacedBox(
            x = shape.body.position.x.toPixels(),
            y = shape.body.position.y.toPixels(),
            halfWidth = (c * shape.widthPx + s * shape.heightPx) * itemScale / 2f,
            halfHeight = (s * shape.widthPx + c * shape.heightPx) * itemScale / 2f,
        )
    }

    /** A growing item must not push through a wall or into the corner control. */
    private fun keepInside(item: CreatedPhysicsItem) {
        val box = boxOf(item)
        var x = maxOf(box.halfWidth, minOf(box.x, widthPx - box.halfWidth))
        var y = maxOf(box.halfHeight, minOf(box.y, heightPx - box.halfHeight))
        if (keepOutPx > 0f) {
            val intoX = x + box.halfWidth - (widthPx - keepOutPx)
            val intoY = y + box.halfHeight - (heightPx - keepOutPx)
            if (intoX > 0f && intoY > 0f) {
                if (intoX < intoY) x -= intoX else y -= intoY
            }
        }
        if (x != box.x || y != box.y) {
            val body = item.physicalShape.body
            body.setTransform(Vec2(x.toMeters(), y.toMeters()), body.angle)
        }
    }

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
        items.filter { it.isOwn }.forEach { item -> byKey[item.spec.key]?.let { item.spec = it } }
        if (itemScale == targetScale) spawnMissing(popIn)
    }

    private fun spawnMissing(popIn: Boolean) {
        val present = HashSet<Any>(pendingRespawns.keys)
        items.filter { it.isOwn }.forEach { present += it.spec.key }
        specs.filter { it.key !in present }.forEach { spec ->
            spawn(spec, freeSlot(spec), isOwn = true, popIn = popIn)
        }
    }

    private fun World.createKeepOut() {
        val half = keepOutPx.toMeters() / 2f
        val body = createBody(
            BodyDef().apply {
                type = BodyType.STATIC
                position.set(widthPx.toMeters() - half, heightPx.toMeters() - half)
            },
        )
        body.createFixture(PolygonShape().apply { setAsBox(half, half) }, 0f)
    }

    /** The first free spot or, when the pile leaves none, the least crowded one. */
    private fun freeSlot(spec: SandboxItem): Pair<Float, Float> {
        val placed = items.map(::boxOf)
        val (itemWidth, itemHeight) = with(density) { spec.width.toPx() * itemScale to spec.height.toPx() * itemScale }
        val spot = firstFreeSpot(itemWidth, itemHeight, placed, widthPx, heightPx, density.density, keepOutPx)
            ?: leastCrowdedSpot(itemWidth, itemHeight, placed, widthPx, heightPx, density.density, keepOutPx)
        return spot.x to spot.y
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
    fun isAtRest(): Boolean = itemScale == targetScale && pendingRespawns.isEmpty() && throwCandidate == null &&
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
        payloadFor: ((Any) -> String?)? = null,
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
                payload = payloadFor?.invoke(item.spec.key),
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
        val inset = with(density) { maxOf(spec.width, spec.height).toPx() } * itemScale / 2f + 4f
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
        val item = CreatedPhysicsItem(nextId++, spec, shape, isOwn, popIn)
        if (itemScale != 1f) applyScale(item, itemScale)
        items += item
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
