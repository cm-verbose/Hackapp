package com.example.hackapp.ui.components.map

import kotlin.math.max
import kotlin.math.min

const val MIN_MAP_ZOOM = 1f
const val MAX_MAP_ZOOM = 6f

/** A rectangle in pixels. */
data class MapBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Zoom and pan applied on top of the map's fitted position:
 * `screen = pan + fitted * zoom`. Plain floats keep this logic testable on the JVM.
 */
data class MapViewport(val zoom: Float = MIN_MAP_ZOOM, val panX: Float = 0f, val panY: Float = 0f) {
    fun screenX(fittedX: Float) = panX + fittedX * zoom
    fun screenY(fittedY: Float) = panY + fittedY * zoom
    fun fittedX(screenX: Float) = (screenX - panX) / zoom
    fun fittedY(screenY: Float) = (screenY - panY) / zoom

    /** Changes the zoom while the point under ([focalX], [focalY]) stays where it is. */
    fun zoomedTo(target: Float, focalX: Float, focalY: Float): MapViewport {
        val next = target.coerceIn(MIN_MAP_ZOOM, MAX_MAP_ZOOM)
        val ratio = next / zoom
        return MapViewport(next, focalX - (focalX - panX) * ratio, focalY - (focalY - panY) * ratio)
    }

    fun pannedBy(dx: Float, dy: Float) = copy(panX = panX + dx, panY = panY + dy)

    /**
     * Keeps [content] reachable inside [visible]. On an axis where the content fits it is centered;
     * otherwise its edges cannot move inside the visible area, so the map can never be lost.
     */
    fun clamped(content: MapBounds, visible: MapBounds): MapViewport {
        val z = zoom.coerceIn(MIN_MAP_ZOOM, MAX_MAP_ZOOM)
        return MapViewport(
            zoom = z,
            panX = clampAxis(panX, z, content.left, content.right, visible.left, visible.right),
            panY = clampAxis(panY, z, content.top, content.bottom, visible.top, visible.bottom),
        )
    }

    private fun clampAxis(
        pan: Float,
        z: Float,
        contentStart: Float,
        contentEnd: Float,
        visibleStart: Float,
        visibleEnd: Float,
    ): Float {
        if ((contentEnd - contentStart) * z <= visibleEnd - visibleStart) {
            return (visibleStart + visibleEnd) / 2f - z * (contentStart + contentEnd) / 2f
        }
        val lowest = visibleEnd - z * contentEnd
        val highest = visibleStart - z * contentStart
        return pan.coerceIn(min(lowest, highest), max(lowest, highest))
    }
}

/** Double-tap steps through 1x, 2x, 4x and 6x, then returns to 1x. */
fun nextDoubleTapZoom(zoom: Float): Float =
    if (zoom >= MAX_MAP_ZOOM - 0.01f) MIN_MAP_ZOOM else min(zoom * 2f, MAX_MAP_ZOOM)

/** A count badge at its fitted (1x) position. */
data class MapBadge(val id: String, val x: Float, val y: Float, val count: Int, val selected: Boolean)

/** Selected Districts first, then the highest counts. The id keeps the order stable. */
private val BadgePriority = compareByDescending<MapBadge> { it.selected }
    .thenByDescending { it.count }
    .thenBy { it.id }

/**
 * The badges to show at [zoom]. Badges keep a fixed [diameter], so spreading the map apart reveals
 * lower-count ones. Selected badges always show. Panning cannot change the result.
 */
fun visibleBadgeIds(badges: List<MapBadge>, zoom: Float, diameter: Float): Set<String> {
    val kept = ArrayList<MapBadge>(badges.size)
    val minDistanceSquared = (diameter / zoom).let { it * it }
    badges.sortedWith(BadgePriority).forEach { badge ->
        val clear = kept.none { other ->
            val dx = other.x - badge.x
            val dy = other.y - badge.y
            dx * dx + dy * dy < minDistanceSquared
        }
        if (badge.selected || clear) kept += badge
    }
    return kept.mapTo(LinkedHashSet()) { it.id }
}

/** The visible badge under a screen point, nearest first, or null. */
fun badgeAt(
    badges: List<MapBadge>,
    visibleIds: Set<String>,
    viewport: MapViewport,
    screenX: Float,
    screenY: Float,
    radius: Float,
): String? = badges
    .asSequence()
    .filter { it.id in visibleIds }
    .map { badge ->
        val dx = viewport.screenX(badge.x) - screenX
        val dy = viewport.screenY(badge.y) - screenY
        badge to dx * dx + dy * dy
    }
    .filter { (_, distanceSquared) -> distanceSquared <= radius * radius }
    .minByOrNull { (_, distanceSquared) -> distanceSquared }
    ?.first
    ?.id
