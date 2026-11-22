package com.example.hackapp.ui.components.map

import android.graphics.Rect as AndroidRect
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.example.hackapp.citypack.DistrictMapData
import com.example.hackapp.citypack.DistrictShape
import kotlin.math.min
import kotlin.math.roundToInt

/** Map units (districts.json viewBox) ↔ screen pixels. */
class MapTransform(val origin: Offset, val scale: Float) {
    fun toScreen(p: Offset) = origin + p * scale
    fun toMap(p: Offset) = (p - origin) / scale
}

class ParsedDistrict(val shape: DistrictShape, val path: Path, val bounds: Rect, val hit: Region) {
    val id get() = shape.id
    val label get() = Offset(shape.label[0], shape.label[1])
}

/** Parse once per City Pack map (cheap to keep while the map is on screen). */
@Composable
fun rememberParsedDistricts(map: DistrictMapData): List<ParsedDistrict> = remember(map) {
    map.districts.map { d ->
        val p = PathParser().parsePathString(d.path).toPath()
        val b = RectF().also { p.asAndroidPath().computeBounds(it, true) }
        val clip = Region(AndroidRect(b.left.toInt(), b.top.toInt(), b.right.toInt() + 1, b.bottom.toInt() + 1))
        ParsedDistrict(d, p, Rect(b.left, b.top, b.right, b.bottom), Region().apply { setPath(p.asAndroidPath(), clip) })
    }
}

/**
 * City-agnostic map base used by DistrictMap and ItineraryRouteMap.
 * Everything city-specific comes from [map] (the active City Pack's districts.json).
 *
 * @param focus districts to zoom to (null = whole city)
 * @param fillOf fill colour per district (states are decided by the caller)
 * @param overlay drawn in map units on top of the districts (routes, highlights)
 * @param content composables placed with [MapTransform.toScreen] (badges, markers)
 * @param viewportState enables pinch, two-finger rotation, drag and double-tap. [content] then receives the fitted
 *   (1x) transform and must apply [MapViewportState.viewport] in a layout or draw lambda
 * @param tapTarget resolves a tap at a screen point before the District shapes, such as a badge
 * @param onEmptyTap called for a tap outside every District
 */
@Composable
fun CityMap(
    map: DistrictMapData,
    modifier: Modifier = Modifier,
    focus: Set<String>? = null,
    focusPadding: Float = 0.25f,
    fillOf: (ParsedDistrict) -> Color,
    onDistrictTap: ((String) -> Unit)? = null,
    overlay: DrawScope.(MapTransform) -> Unit = {},
    viewportState: MapViewportState? = null,
    tapTarget: ((Offset) -> String?)? = null,
    onEmptyTap: (() -> Unit)? = null,
    content: @Composable BoxScope.(MapTransform, List<ParsedDistrict>) -> Unit = { _, _ -> },
) {
    val districts = rememberParsedDistricts(map)
    val stroke = MaterialTheme.colorScheme.surface
    val scope = rememberCoroutineScope()
    val currentOnTap by rememberUpdatedState(onDistrictTap)
    val currentTapTarget by rememberUpdatedState(tapTarget)
    val currentOnEmptyTap by rememberUpdatedState(onEmptyTap)
    val handlesTaps = onDistrictTap != null || viewportState != null
    val (vbX, vbY, vbW, vbH) = map.viewBox
    val area: Rect = remember(map, focus) {
        val sel = focus?.let { f -> districts.filter { it.id in f } }.orEmpty()
        if (sel.isEmpty()) Rect(vbX, vbY, vbX + vbW, vbY + vbH)
        else sel.map { it.bounds }.reduce { a, b -> Rect(min(a.left, b.left), min(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
            .let { r -> r.inflate(maxOf(r.width, r.height) * focusPadding) }
    }

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val s = min(w / area.width, h / area.height)
        val t = MapTransform(Offset(w / 2f - area.center.x * s, h / 2f - area.center.y * s), s)

        if (viewportState != null) {
            val fitted = remember(districts, t.scale, t.origin) { fittedBounds(districts, t) }
            SideEffect { viewportState.setLayout(fitted, w, h) }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .then(if (viewportState != null) Modifier.clipToBounds() else Modifier)
                .then(
                    if (viewportState == null) Modifier
                    else Modifier.pointerInput(viewportState) {
                        detectTransformGestures { centroid, pan, zoom, rotation ->
                            viewportState.applyGesture(centroid, pan, zoom, rotation)
                        }
                    }
                )
                .then(
                    if (!handlesTaps) Modifier
                    else Modifier.pointerInput(districts, t.scale, t.origin, viewportState) {
                        detectTapGestures(
                            onDoubleTap = viewportState?.let { state -> { tap -> state.animateDoubleTap(scope, tap) } },
                            onTap = { tap ->
                                val id = currentTapTarget?.invoke(tap) ?: run {
                                    val vp = viewportState?.viewport ?: MapViewport()
                                    val p = t.toMap(Offset(vp.fittedX(tap.x, tap.y), vp.fittedY(tap.x, tap.y)))
                                    districts.lastOrNull { it.hit.contains(p.x.roundToInt(), p.y.roundToInt()) }?.id
                                }
                                if (id != null) currentOnTap?.invoke(id) else currentOnEmptyTap?.invoke()
                            },
                        )
                    }
                ),
        ) {
            val vp = viewportState?.viewport ?: MapViewport()
            val drawn = MapTransform(
                Offset(vp.screenX(t.origin.x, t.origin.y), vp.screenY(t.origin.x, t.origin.y)),
                s * vp.zoom,
            )
            translate(drawn.origin.x, drawn.origin.y) {
                rotate(vp.rotation, pivot = Offset.Zero) {
                    scale(drawn.scale, drawn.scale, pivot = Offset.Zero) {
                        districts.forEach { d ->
                            drawPath(d.path, fillOf(d))
                            drawPath(d.path, stroke, style = Stroke(width = 1.5f * density.density / drawn.scale))
                        }
                        overlay(drawn)
                    }
                }
            }
        }
        content(t, districts)
    }
}

private fun fittedBounds(districts: List<ParsedDistrict>, t: MapTransform): MapBounds {
    if (districts.isEmpty()) return MapBounds(t.origin.x, t.origin.y, t.origin.x, t.origin.y)
    val topLeft = t.toScreen(Offset(districts.minOf { it.bounds.left }, districts.minOf { it.bounds.top }))
    val bottomRight = t.toScreen(Offset(districts.maxOf { it.bounds.right }, districts.maxOf { it.bounds.bottom }))
    return MapBounds(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
}
