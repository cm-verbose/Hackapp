// app/src/main/java/com/ets/hackapp/ui/components/map/ItineraryRouteMap.kt
package com.ets.hackapp.ui.components.map

import androidx.compose.foundation.background
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
import com.ets.hackapp.citypack.CityPack
import com.ets.hackapp.citypack.DistrictMapData
import com.ets.hackapp.citypack.GeoPoint
import com.ets.hackapp.citypack.LocalCityPack
import com.ets.hackapp.ui.theme.AppTheme
import com.ets.hackapp.ui.theme.StopType
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
    val stopColors = AppTheme.stopTypeColors
    val visited = remember(stops) { stops.map { it.districtId }.toSet() }
    val groupTint = remember(cityPack) { cityPack.districtGroups.associate { it.id to Color(it.colors.first()) } }
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
    val dash = with(LocalDensity.current) { PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) }

    CityMap(
        map = map,
        modifier = modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.large).background(cs.surfaceContainerLow),
        focus = visited,
        fillOf = { d -> if (d.id in visited) cs.primaryContainer else districtBaseFill(cs, d.shape.groupId, groupTint) },
        overlay = { t ->
            if (points.size > 1) {
                val route = Path().apply {
                    moveTo(points.first().second.x, points.first().second.y)
                    points.drop(1).forEach { (_, p) -> lineTo(p.x, p.y) }
                }
                drawPath(route, cs.primary, style = Stroke(width = 3.dp.toPx() / t.scale, cap = StrokeCap.Round, pathEffect = dash))
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
                    .background(c.container, CircleShape)
                    .border(2.dp, cs.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("${stop.order}", style = MaterialTheme.typography.labelMedium, color = c.content) }
        }
    }
}
