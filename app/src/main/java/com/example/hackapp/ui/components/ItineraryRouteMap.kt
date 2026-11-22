package com.example.hackapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma "ItineraryRouteMap" (53:1443), review / underway screens: the Stops of an Itinerary as
 * numbered pins, in visit order. [stops] should carry badge = label = the Stop's position.
 *
 * Figma shows pins only. [showRouteLine] draws a dashed line between consecutive pins as a
 * lightweight stand-in until the real maps-compose Polyline is passed in [mapContent].
 */
@Composable
fun ItineraryRouteMap(
    stops: List<MapPin>,
    modifier: Modifier = Modifier,
    onStopClick: ((MapPin) -> Unit)? = null,
    height: Dp = 200.dp,
    caption: String? = stringResource(R.string.map_district_boundaries),
    showRouteLine: Boolean = false,
    pinMarker: @Composable (MapPin) -> Unit = { DefaultMapPinMarker(it) },
    mapContent: @Composable BoxScope.() -> Unit = {},
) {
    val lineColor = MaterialTheme.colorScheme.primary
    MapFrame(
        height = height,
        caption = caption,
        pins = stops,
        onPinClick = onStopClick,
        pinMarker = pinMarker,
        modifier = modifier,
    ) {
        mapContent()
        if (showRouteLine && stops.size > 1) {
            Canvas(Modifier.fillMaxSize()) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                stops.zipWithNext().forEach { (a, b) ->
                    drawLine(
                        color = lineColor,
                        start = Offset(a.x * size.width, a.y * size.height),
                        end = Offset(b.x * size.width, b.y * size.height),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = dash,
                    )
                }
            }
        }
    }
}

internal val PreviewRoutePins = listOf(
    MapPin("1", "1", "1", x = 0.28f, y = 0.38f),
    MapPin("2", "2", "2", x = 0.49f, y = 0.58f),
    MapPin("3", "3", "3", x = 0.65f, y = 0.78f),
    MapPin("4", "4", "4", x = 0.83f, y = 0.53f),
)

@Preview(name = "ItineraryRouteMap", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun ItineraryRouteMapPreview() = HackappTheme {
    ItineraryRouteMap(stops = PreviewRoutePins)
}

@Preview(name = "ItineraryRouteMap · route line", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun ItineraryRouteMapLinePreview() = HackappTheme {
    ItineraryRouteMap(stops = PreviewRoutePins, showRouteLine = true)
}
