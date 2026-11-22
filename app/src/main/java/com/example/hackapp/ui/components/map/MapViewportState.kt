package com.example.hackapp.ui.components.map

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val ANIMATION_MILLIS = 220
private const val FOCUS_ANIMATION_MILLIS = 900

/** A focused District fills this share of the visible map, leaving room around it. */
private const val FOCUS_FILL = 0.6f

/**
 * Zoom, rotation and pan of an interactive [CityMap]. Read [viewport] only in draw or layout lambdas, so a
 * gesture frame redraws the map without recomposing the District list.
 */
@Stable
class MapViewportState(initial: MapViewport = MapViewport()) {
    var viewport by mutableStateOf(initial)
        private set

    private var content: MapBounds? = null
    private var width = 0f
    private var height = 0f
    internal val canvasWidth get() = width
    internal val canvasHeight get() = height
    private var insetTop = 0f
    private var insetBottom = 0f
    private var animation: Job? = null

    /** Called by [CityMap] with the fitted District bounds and the canvas size. */
    internal fun setLayout(contentBounds: MapBounds, canvasWidth: Float, canvasHeight: Float) {
        if (content == contentBounds && width == canvasWidth && height == canvasHeight) return
        content = contentBounds
        width = canvasWidth
        height = canvasHeight
        viewport = constrain(viewport)
    }

    /** Height covered by the top overlay, in pixels. The map stays reachable below it. */
    fun setTopInset(value: Float) {
        if (insetTop == value) return
        insetTop = value
        reclamp()
    }

    /** Height covered by the bottom overlay. Following it frame by frame moves the map with the sheet. */
    fun setBottomInset(value: Float) {
        if (insetBottom == value) return
        insetBottom = value
        reclamp()
    }

    private fun reclamp() {
        if (animation?.isActive != true) viewport = constrain(viewport)
    }

    internal fun applyGesture(centroid: Offset, pan: Offset, zoomChange: Float, rotationChange: Float) {
        animation?.cancel()
        viewport = constrain(
            viewport
                .zoomedTo(viewport.zoom * zoomChange, centroid.x, centroid.y)
                .rotatedBy(rotationChange, centroid.x, centroid.y)
                .pannedBy(pan.x, pan.y),
        )
    }

    internal fun animateDoubleTap(scope: CoroutineScope, focal: Offset) {
        val start = viewport
        val target = nextDoubleTapZoom(start.zoom)
        animateWith(scope) { fraction ->
            start.zoomedTo(start.zoom + (target - start.zoom) * fraction, focal.x, focal.y)
        }
    }

    fun reset(scope: CoroutineScope) {
        val start = viewport
        val target = constrain(MapViewport())
        animateWith(scope) { fraction ->
            MapViewport(
                zoom = start.zoom + (target.zoom - start.zoom) * fraction,
                panX = start.panX + (target.panX - start.panX) * fraction,
                panY = start.panY + (target.panY - start.panY) * fraction,
                rotation = start.rotation * (1f - fraction),
            )
        }
    }

    /**
     * Zooms, without rotation, so the fitted (1x) rectangle [left]..[bottom] fills the visible map,
     * at no less than [minZoom].
     */
    fun animateToFitted(scope: CoroutineScope, left: Float, top: Float, right: Float, bottom: Float, minZoom: Float = MIN_MAP_ZOOM) {
        if (width <= 0f || height <= 0f) return
        val visibleTop = insetTop
        val visibleHeight = (height - insetBottom - insetTop).coerceAtLeast(1f)
        val fit = minOf(width / (right - left).coerceAtLeast(1f), visibleHeight / (bottom - top).coerceAtLeast(1f)) * FOCUS_FILL
        val zoom = fit.coerceIn(minZoom.coerceAtMost(MAX_MAP_ZOOM), MAX_MAP_ZOOM)
        val target = MapViewport(
            zoom = zoom,
            panX = width / 2f - zoom * (left + right) / 2f,
            panY = visibleTop + visibleHeight / 2f - zoom * (top + bottom) / 2f,
        )
        val start = viewport
        animateWith(scope, FOCUS_ANIMATION_MILLIS) { fraction ->
            MapViewport(
                zoom = start.zoom + (target.zoom - start.zoom) * fraction,
                panX = start.panX + (target.panX - start.panX) * fraction,
                panY = start.panY + (target.panY - start.panY) * fraction,
                rotation = start.rotation * (1f - fraction),
            )
        }
    }

    private fun animateWith(scope: CoroutineScope, millis: Int = ANIMATION_MILLIS, frame: (Float) -> MapViewport) {
        animation?.cancel()
        animation = scope.launch {
            animate(0f, 1f, animationSpec = tween(millis, easing = FastOutSlowInEasing)) { fraction, _ ->
                viewport = constrain(frame(fraction))
            }
        }
    }

    private fun constrain(value: MapViewport): MapViewport {
        val bounds = content ?: return value
        return value.clamped(bounds, MapBounds(0f, insetTop, width, (height - insetBottom).coerceAtLeast(insetTop)))
    }

    companion object {
        val Saver: Saver<MapViewportState, List<Float>> = Saver(
            save = { listOf(it.viewport.zoom, it.viewport.panX, it.viewport.panY, it.viewport.rotation) },
            restore = { MapViewportState(MapViewport(it[0], it[1], it[2], it[3])) },
        )
    }
}

@Composable
fun rememberMapViewportState(): MapViewportState =
    rememberSaveable(saver = MapViewportState.Saver) { MapViewportState() }
