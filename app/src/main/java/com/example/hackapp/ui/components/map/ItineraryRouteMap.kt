package com.example.hackapp.ui.components.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.hackapp.R
import com.example.hackapp.citypack.CityPack
import com.example.hackapp.citypack.DistrictMapData
import com.example.hackapp.citypack.GeoPoint
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.ui.theme.StopType
import kotlin.math.roundToInt

/** One Stop of the Itinerary, in visiting order (glossary: Stop = Event or Meal). */
data class RouteStop(
    val order: Int,
    val districtId: String,
    val type: StopType,
    val location: GeoPoint? = null,
    /** The Stop's name, read by TalkBack when the marker can be tapped. */
    val name: String? = null,
)

/**
 * Figma: ItineraryRouteMap — the City Pack DistrictMap zoomed to the Itinerary's Districts,
 * Legs drawn as a dashed line, Stops as numbered StopMarkers coloured by StopType.
 * Positions use the pack's geo projection when the Stop has coordinates, otherwise the District's label anchor.
 * With [onStopClick] the markers can be tapped, and the one at [highlightedOrder] is enlarged and outlined.
 */
@Composable
fun ItineraryRouteMap(
    map: DistrictMapData,
    stops: List<RouteStop>,
    modifier: Modifier = Modifier,
    cityPack: CityPack = LocalCityPack.current,
    highlightedOrder: Int? = null,
    onStopClick: ((RouteStop) -> Unit)? = null,
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
    val description = pluralStringResource(R.plurals.map_route_description, stops.size, stops.size)
    val dash = with(LocalDensity.current) { PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) }

    CityMap(
        map = map,
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(MaterialTheme.shapes.large)
            .background(cs.surfaceContainerLow)
            .semantics { contentDescription = description },
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
        val half = with(LocalDensity.current) { MarkerTouchSize.toPx() / 2 }
        points.forEach { (stop, p) ->
            val c = stopColors[stop.type]
            val s = t.toScreen(p)
            val highlighted = stop.order == highlightedOrder
            val markerLabel = stringResource(R.string.strip_stop, stop.order.toString(), stop.name.orEmpty())
            val interaction = if (onStopClick == null) {
                Modifier.clearAndSetSemantics {}
            } else {
                Modifier
                    .clearAndSetSemantics {
                        contentDescription = markerLabel
                        role = Role.Button
                        selected = highlighted
                        onClick {
                            onStopClick(stop)
                            true
                        }
                    }
                    .clickable { onStopClick(stop) }
            }
            Box(
                Modifier
                    .zIndex(if (highlighted) 1f else 0f)
                    .size(MarkerTouchSize)
                    .offset { IntOffset((s.x - half).roundToInt(), (s.y - half).roundToInt()) }
                    .then(interaction),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (highlighted) 32.dp else 24.dp)
                        .background(c, CircleShape)
                        .border(2.dp, if (highlighted) cs.onSurface else cs.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("${stop.order}", style = MaterialTheme.typography.labelMedium, color = stopColors.onStopType) }
            }
        }
    }
}

private val MarkerTouchSize = 48.dp

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
