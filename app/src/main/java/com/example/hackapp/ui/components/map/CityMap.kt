package com.example.hackapp.ui.components.map

import android.graphics.Rect as AndroidRect
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
    content: @Composable BoxScope.(MapTransform, List<ParsedDistrict>) -> Unit = { _, _ -> },
) {
    val districts = rememberParsedDistricts(map)
    val stroke = MaterialTheme.colorScheme.surface
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

        Canvas(
            Modifier
                .fillMaxSize()
                .then(
                    if (onDistrictTap == null) Modifier
                    else Modifier.pointerInput(districts, t.scale, t.origin) {
                        detectTapGestures { tap ->
                            val p = t.toMap(tap)
                            districts.lastOrNull { it.hit.contains(p.x.roundToInt(), p.y.roundToInt()) }?.let { onDistrictTap(it.id) }
                        }
                    }
                ),
        ) {
            translate(t.origin.x, t.origin.y) {
                scale(s, s, pivot = Offset.Zero) {
                    districts.forEach { d ->
                        drawPath(d.path, fillOf(d))
                        drawPath(d.path, stroke, style = Stroke(width = 1.5f * density.density / s))
                    }
                    overlay(t)
                }
            }
        }
        content(t, districts)
    }
}
