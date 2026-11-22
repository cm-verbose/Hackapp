package com.example.hackapp.ui.components.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.example.hackapp.ui.theme.HackappTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.hackapp.citypack.CityPack
import com.example.hackapp.citypack.DistrictMapData
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.components.DistrictEventCount
import kotlin.math.roundToInt

enum class DistrictState { Available, Previewed, Selected, Unavailable }

/** Figma: DistrictPickMap (wraps DistrictMap · City=<pack>). Planning › District pick. */
@Composable
fun DistrictMap(
    map: DistrictMapData,
    stateOf: (String) -> DistrictState,
    eventCountOf: (String) -> Int?,
    onDistrictClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    cityPack: CityPack = LocalCityPack.current,
    badgeSize: Dp = 32.dp,
) {
    val cs = MaterialTheme.colorScheme
    val groupTint = remember(cityPack) { cityPack.districtGroups.mapNotNull { g -> g.colors.firstOrNull()?.let { g.id to Color(it) } }.toMap() }
    CityMap(
        map = map,
        modifier = modifier.fillMaxWidth().aspectRatio(map.viewBox[2] / map.viewBox[3]),
        fillOf = { d ->
            when (stateOf(d.id)) {
                DistrictState.Selected -> cs.primary
                DistrictState.Previewed -> cs.secondaryContainer
                DistrictState.Unavailable -> cs.outlineVariant.copy(alpha = 0.4f)
                DistrictState.Available -> districtBaseFill(cs, d.shape.groupId, groupTint)
            }
        },
        onDistrictTap = { id -> if (stateOf(id) != DistrictState.Unavailable) onDistrictClick(id) },
    ) { t, districts ->
        val half = with(LocalDensity.current) { (badgeSize / 2).toPx() }
        districts.forEach { d ->
            val count = eventCountOf(d.id) ?: return@forEach
            val p = t.toScreen(d.label)
            Box(Modifier.offset { IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt()) }) {
                // Eric's component (branch components/Eric): result, badgeColor, badgeSize.
                DistrictEventCount(result = count, badgeColor = if (count == 0) cs.outline else cs.primary, badgeSize = badgeSize)
            }
        }
    }
}

/** Default fill: City Pack group tint (Toronto divisions), else borough vs linked city. Not @Composable: called while drawing. */
fun districtBaseFill(cs: ColorScheme, groupId: String?, groupTint: Map<String, Color>): Color = groupTint[groupId]?.copy(alpha = 0.45f) ?: if (groupId == "linked") cs.surfaceVariant else cs.surfaceContainerHighest

@Preview(name = "DistrictMap", showBackground = true, backgroundColor = 0xFFF0F0F1, widthDp = 380)
@Composable
private fun DistrictMapPreview() = HackappTheme {
    DistrictMap(
        map = PreviewDistrictMap,
        stateOf = { when (it) { "a" -> DistrictState.Selected; "b" -> DistrictState.Previewed; "c" -> DistrictState.Unavailable; else -> DistrictState.Available } },
        eventCountOf = { when (it) { "a" -> 14; "b" -> 8; "c" -> 0; else -> null } },
        onDistrictClick = {},
    )
}
