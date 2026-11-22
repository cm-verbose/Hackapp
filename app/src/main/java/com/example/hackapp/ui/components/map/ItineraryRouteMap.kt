package com.example.hackapp.ui.components.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.PathMeasure
import com.example.hackapp.ui.motion.HackappMotion
import com.example.hackapp.ui.motion.popIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.hackapp.citypack.CityPack
import com.example.hackapp.citypack.DistrictMapData
import com.example.hackapp.citypack.GeoPoint
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.ui.theme.StopType
import kotlin.math.roundToInt

/** One Stop of the Itinerary, in visiting order (glossary: Stop = Event or Meal). */
data class RouteStop(val order: Int, val districtId: String, val type: StopType, val location: GeoPoint? = null)

/**
 * Figma: ItineraryRouteMap — the City Pack DistrictMap zoomed to the Itinerary's Districts,
 * Legs drawn as a dashed line, Stops as numbered StopMarkers coloured by StopType.
 * Positions use the pack's geo projection when the Stop has coordinates, otherwise the District's label anchor.
 */
@Composable
fun ItineraryRouteMap(
    map: DistrictMapData,
    stops: List<RouteStop>,
    modifier: Modifier = Modifier,
    cityPack: CityPack = LocalCityPack.current,
) {
    val cs = MaterialTheme.colorScheme
    val stopColors = HackappTheme.stopTypeColors
    val visited = remember(stops) { stops.map { it.districtId }.toSet() }
    val groupTint = remember(cityPack) { cityPack.districtGroups.mapNotNull { g -> g.colors.firstOrNull()?.let { g.id to Color(it) } }.toMap() }
    val labels = remember(map) { map.districts.associate { it.id to Offset(it.label[0], it.label[1]) } }
    // Map-unit point for every Stop; Stops sharing a District without coordinates are fanned around its label.
    val points = remember(map, stops) {
        stops.groupBy { it.districtId }.flatMap { (district, inDistrict) ->
            inDistrict.mapIndexed { i, s ->
                s to (s.location?.let { map.projection?.project(it) }
                    ?: (labels[district] ?: Offset.Zero) + Offset(((i % 2) * 2 - 1) * 8f, (i / 2) * 10f - 4f))
            }
        }.sortedBy { it.first.order }
    }
    // The route draws itself stop by stop, then a dot keeps travelling along it.
    val drawn = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { drawn.animateTo(1f, tween(1100 + 250 * points.size, delayMillis = 250, easing = HackappMotion.Decelerate)) }
    val loop = rememberInfiniteTransition(label = "traveller")
    val travel by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "travel")
    val measure = remember(points) {
        PathMeasure().also { m ->
            m.setPath(Path().apply {
                points.firstOrNull()?.let { moveTo(it.second.x, it.second.y) }
                points.drop(1).forEach { (_, p) -> lineTo(p.x, p.y) }
            }, false)
        }
    }
    val dash = with(LocalDensity.current) { PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) }

    CityMap(
        map = map,
        modifier = modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.large).background(cs.surfaceContainerLow),
        focus = visited,
        fillOf = { d -> if (d.id in visited) cs.primaryContainer else districtBaseFill(cs, d.shape.groupId, groupTint) },
        overlay = { t ->
            if (points.size > 1 && measure.length > 0f) {
                val part = Path()
                measure.getSegment(0f, measure.length * drawn.value, part, true)
                drawPath(part, cs.primary, style = Stroke(width = 3.dp.toPx() / t.scale, cap = StrokeCap.Round, pathEffect = dash))
                if (drawn.value >= 1f) {
                    val at = measure.getPosition(measure.length * travel)
                    drawCircle(cs.primary.copy(alpha = 0.25f), 9.dp.toPx() / t.scale, at)
                    drawCircle(cs.primary, 5.dp.toPx() / t.scale, at)
                }
            }
        },
    ) { t, _ ->
        val half = with(LocalDensity.current) { 12.dp.toPx() }
        points.forEach { (stop, p) ->
            val c = stopColors[stop.type]
            val s = t.toScreen(p)
            Box(
                Modifier
                    .size(24.dp)
                    .offset { IntOffset((s.x - half).roundToInt(), (s.y - half).roundToInt()) }
                    .background(c, CircleShape)
                    .border(2.dp, cs.surface, CircleShape)
                    .popIn(delayMillis = 250 + stop.order * 220),
                contentAlignment = Alignment.Center,
            ) { Text("${stop.order}", style = MaterialTheme.typography.labelMedium, color = stopColors.onStopType) }
        }
    }
}

@Preview(name = "ItineraryRouteMap", showBackground = true, backgroundColor = 0xFFF0F0F1, widthDp = 380)
@Composable
private fun ItineraryRouteMapPreview() = HackappTheme {
    ItineraryRouteMap(
        map = PreviewDistrictMap,
        stops = listOf(
            RouteStop(1, "a", StopType.Music),
            RouteStop(2, "a", StopType.Meal),
            RouteStop(3, "b", StopType.Theatre),
        ),
    )
}
