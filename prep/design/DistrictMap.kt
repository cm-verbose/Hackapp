// app/src/main/java/com/ets/hackapp/ui/components/map/DistrictMap.kt  (replaces the previous DistrictMap.kt)
package com.ets.hackapp.ui.components.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ets.hackapp.citypack.CityPack
import com.ets.hackapp.citypack.DistrictMapData
import com.ets.hackapp.citypack.LocalCityPack
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
) {
    val cs = MaterialTheme.colorScheme
    val groupTint = remember(cityPack) { cityPack.districtGroups.associate { it.id to Color(it.colors.first()) } }
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
        val half = with(LocalDensity.current) { 10.dp.toPx() }
        districts.forEach { d ->
            val count = eventCountOf(d.id) ?: return@forEach
            val p = t.toScreen(d.label)
            DistrictEventCount(count, Modifier.size(20.dp).offset { IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt()) })
        }
    }
}

/** Default fill: City Pack group tint (Toronto divisions), else borough vs linked city. Not @Composable: called while drawing. */
fun districtBaseFill(cs: ColorScheme, groupId: String?, groupTint: Map<String, Color>): Color = groupTint[groupId]?.copy(alpha = 0.45f) ?: if (groupId == "linked") cs.surfaceVariant else cs.surfaceContainerHighest

/** Figma: DistrictEventCount */
@Composable
fun DistrictEventCount(count: Int, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.background(if (count == 0) cs.outline else cs.primary, CircleShape), contentAlignment = Alignment.Center) {
        Text("$count", style = MaterialTheme.typography.labelSmall, color = cs.onPrimary)
    }
}
