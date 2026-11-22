package com.example.hackapp.ui.components.map

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

const val MIN_MAP_ZOOM = 1f
const val MAX_MAP_ZOOM = 6f

/** Below this zoom Districts show count badges. From it they show their names. */
const val MAP_NAMES_ZOOM = 2f

/** A rectangle in pixels. */
data class MapBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Zoom, rotation (degrees, clockwise) and pan applied on top of the map's fitted position:
 * `screen = pan + zoom * R(rotation) * fitted`. Plain floats keep this logic testable on the JVM.
 */
data class MapViewport(
    val zoom: Float = MIN_MAP_ZOOM,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val rotation: Float = 0f,
) {
    private val radians get() = Math.toRadians(rotation.toDouble())
    private val cosine get() = cos(radians).toFloat()
    private val sine get() = sin(radians).toFloat()

    fun screenX(fittedX: Float, fittedY: Float) = panX + zoom * (fittedX * cosine - fittedY * sine)
    fun screenY(fittedX: Float, fittedY: Float) = panY + zoom * (fittedX * sine + fittedY * cosine)

    fun fittedX(screenX: Float, screenY: Float): Float {
        val dx = (screenX - panX) / zoom
        val dy = (screenY - panY) / zoom
        return dx * cosine + dy * sine
    }

    fun fittedY(screenX: Float, screenY: Float): Float {
        val dx = (screenX - panX) / zoom
        val dy = (screenY - panY) / zoom
        return -dx * sine + dy * cosine
    }

    /** Changes the zoom while the point under ([focalX], [focalY]) stays where it is. */
    fun zoomedTo(target: Float, focalX: Float, focalY: Float): MapViewport {
        val next = target.coerceIn(MIN_MAP_ZOOM, MAX_MAP_ZOOM)
        val ratio = next / zoom
        return copy(
            zoom = next,
            panX = focalX - (focalX - panX) * ratio,
            panY = focalY - (focalY - panY) * ratio,
        )
    }

    /** Turns the map by [degrees] while the point under ([focalX], [focalY]) stays where it is. */
    fun rotatedBy(degrees: Float, focalX: Float, focalY: Float): MapViewport {
        val turn = Math.toRadians(degrees.toDouble())
        val c = cos(turn).toFloat()
        val s = sin(turn).toFloat()
        val dx = panX - focalX
        val dy = panY - focalY
        return copy(
            panX = focalX + dx * c - dy * s,
            panY = focalY + dx * s + dy * c,
            rotation = normalizedDegrees(rotation + degrees),
        )
    }

    fun pannedBy(dx: Float, dy: Float) = copy(panX = panX + dx, panY = panY + dy)

    /**
     * Keeps [content] reachable inside [visible], using its rotated extents. On an axis where the
     * content fits it is centered; otherwise its edges cannot move inside the visible area.
     */
    fun clamped(content: MapBounds, visible: MapBounds): MapViewport {
        val z = zoom.coerceIn(MIN_MAP_ZOOM, MAX_MAP_ZOOM)
        val origin = copy(zoom = z, panX = 0f, panY = 0f)
        val xs = content.corners().map { (x, y) -> origin.screenX(x, y) }
        val ys = content.corners().map { (x, y) -> origin.screenY(x, y) }
        return copy(
            zoom = z,
            panX = clampAxis(panX, xs.min(), xs.max(), visible.left, visible.right),
            panY = clampAxis(panY, ys.min(), ys.max(), visible.top, visible.bottom),
        )
    }

    private fun MapBounds.corners() =
        listOf(left to top, right to top, right to bottom, left to bottom)

    private fun clampAxis(
        pan: Float,
        contentStart: Float,
        contentEnd: Float,
        visibleStart: Float,
        visibleEnd: Float,
    ): Float {
        if (contentEnd - contentStart <= visibleEnd - visibleStart) {
            return (visibleStart + visibleEnd) / 2f - (contentStart + contentEnd) / 2f
        }
        val lowest = visibleEnd - contentEnd
        val highest = visibleStart - contentStart
        return pan.coerceIn(min(lowest, highest), max(lowest, highest))
    }
}

/** The same angle in the range -180 to 180 degrees, which is also the shortest way back to 0. */
fun normalizedDegrees(degrees: Float): Float {
    val wrapped = ((degrees + 180f) % 360f + 360f) % 360f - 180f
    return if (wrapped == -180f) 180f else wrapped
}

/** Double-tap steps through 1x, 2x, 4x and 6x, then returns to 1x. */
fun nextDoubleTapZoom(zoom: Float): Float =
    if (zoom >= MAX_MAP_ZOOM - 0.01f) MIN_MAP_ZOOM else min(zoom * 2f, MAX_MAP_ZOOM)

/** A District name at its fitted (1x) position, with its measured size in pixels. */
data class MapLabel(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val count: Int,
    /** Selected or previewed names always show. */
    val pinned: Boolean,
)

/** Pinned names first, then the highest counts. The id keeps the order stable. */
private val LabelPriority = compareByDescending<MapLabel> { it.pinned }
    .thenByDescending { it.count }
    .thenBy { it.id }

/**
 * The names to show. Names stay upright at a fixed size, so zooming spreads them apart and reveals
 * lower-priority ones. Panning cannot change the result.
 */
fun visibleLabelIds(labels: List<MapLabel>, viewport: MapViewport, gap: Float = 0f): Set<String> {
    val placement = viewport.copy(panX = 0f, panY = 0f)
    class Placed(val x: Float, val y: Float, val label: MapLabel)

    val kept = ArrayList<Placed>(labels.size)
    labels.sortedWith(LabelPriority).forEach { label ->
        val placed = Placed(placement.screenX(label.x, label.y), placement.screenY(label.x, label.y), label)
        val clear = kept.none { other ->
            abs(other.x - placed.x) < (other.label.width + label.width) / 2f + gap &&
                abs(other.y - placed.y) < (other.label.height + label.height) / 2f + gap
        }
        if (label.pinned || clear) kept += placed
    }
    return kept.mapTo(LinkedHashSet()) { it.label.id }
}

/**
 * Where a centered name starts on one axis. A name whose anchor is on screen is nudged fully inside
 * the canvas. One whose anchor has moved off screen leaves with it.
 */
fun labelStart(anchor: Float, size: Float, canvas: Float): Float {
    val start = anchor - size / 2f
    return if (anchor in 0f..canvas && size <= canvas) start.coerceIn(0f, canvas - size) else start
}

/** The visible name under a screen point, nearest first, or null. */
fun labelAt(
    labels: List<MapLabel>,
    visibleIds: Set<String>,
    viewport: MapViewport,
    screenX: Float,
    screenY: Float,
): String? = labels
    .asSequence()
    .filter { it.id in visibleIds }
    .map { label ->
        val dx = viewport.screenX(label.x, label.y) - screenX
        val dy = viewport.screenY(label.x, label.y) - screenY
        Triple(label, dx, dy)
    }
    .filter { (label, dx, dy) -> abs(dx) <= label.width / 2f && abs(dy) <= label.height / 2f }
    .minByOrNull { (_, dx, dy) -> dx * dx + dy * dy }
    ?.first
    ?.id
