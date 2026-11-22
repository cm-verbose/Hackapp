package com.example.hackapp.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma "DistrictMap" (53:1401), PlanningDistrictPickScreen: Districts with their matching
 * Event count. Tapping a pin should open [DistrictPreviewSheet] in a ModalBottomSheet.
 *
 * Per the City Pack, the real map is maps-compose with one Polygon per District; pass it in
 * [mapContent] (maps-compose is not a dependency yet). Pins with count 0 should be [MapPin.muted].
 */
@Composable
fun DistrictMap(
    districts: List<MapPin>,
    onDistrictClick: (MapPin) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    caption: String? = stringResource(R.string.map_district_boundaries),
    pinMarker: @Composable (MapPin) -> Unit = { DefaultMapPinMarker(it) },
    mapContent: @Composable BoxScope.() -> Unit = {},
) {
    MapFrame(
        height = height,
        caption = caption,
        pins = districts,
        onPinClick = onDistrictClick,
        pinMarker = pinMarker,
        modifier = modifier,
        mapContent = mapContent,
    )
}

internal val PreviewDistrictPins = listOf(
    MapPin("rosemont", "Rosemont", "8", x = 0.47f, y = 0.22f),
    MapPin("outremont", "Outremont", "5", x = 0.24f, y = 0.42f),
    MapPin("plateau", "Plateau", "14", x = 0.50f, y = 0.45f),
    MapPin("ville-marie", "Ville-Marie", "22", x = 0.58f, y = 0.72f),
    MapPin("verdun", "Verdun", "0", x = 0.20f, y = 0.85f, muted = true),
)

@Preview(name = "DistrictMap", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun DistrictMapPreview() = HackappTheme {
    DistrictMap(districts = PreviewDistrictPins, onDistrictClick = {})
}
