package com.example.hackapp.ui.components.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.hackapp.citypack.CityPack
import com.example.hackapp.citypack.DistrictMapData
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.components.DistrictEventCount
import com.example.hackapp.ui.theme.HackappTheme
import kotlin.math.roundToInt

/** How one District appears on the pick map. [order] is its visit position when selected. */
data class DistrictMarker(
    val count: Int,
    val pickable: Boolean,
    val showBadge: Boolean,
    val order: Int? = null,
    val previewed: Boolean = false,
)

/**
 * Full-size interactive District map. Badges keep a fixed size, move in the layout phase and
 * recompose only when the set of visible badges changes.
 */
@Composable
fun DistrictPickMap(
    map: DistrictMapData,
    markers: Map<String, DistrictMarker>,
    viewportState: MapViewportState,
    onDistrictTap: (String) -> Unit,
    onEmptyTap: () -> Unit,
    modifier: Modifier = Modifier,
    cityPack: CityPack = LocalCityPack.current,
    badgeSize: Dp = 24.dp,
) {
    val cs = MaterialTheme.colorScheme
    val groupTint = remember(cityPack) {
        cityPack.districtGroups.mapNotNull { g -> g.colors.firstOrNull()?.let { g.id to Color(it) } }.toMap()
    }
    val badgePx = with(LocalDensity.current) { badgeSize.toPx() }
    val currentMarkers by rememberUpdatedState(markers)
    // Published after composition for the tap handler, which runs outside it.
    val badgeHolder = remember { BadgeHolder() }

    CityMap(
        map = map,
        modifier = modifier.fillMaxSize().clipToBounds(),
        viewportState = viewportState,
        fillOf = { d ->
            val marker = currentMarkers[d.id]
            when {
                marker?.order != null -> cs.secondaryContainer
                marker?.previewed == true -> cs.primary
                marker?.pickable != true -> cs.outlineVariant.copy(alpha = 0.4f)
                else -> districtBaseFill(cs, d.shape.groupId, groupTint)
            }
        },
        tapTarget = { tap ->
            badgeAt(badgeHolder.badges, badgeHolder.visible, viewportState.viewport, tap.x, tap.y, badgePx / 2f)
        },
        onDistrictTap = { id -> if (currentMarkers[id]?.pickable == true) onDistrictTap(id) },
        onEmptyTap = onEmptyTap,
    ) { t, districts ->
        val badges = remember(districts, t.scale, t.origin, markers) {
            districts.mapNotNull { d ->
                val marker = markers[d.id]?.takeIf { it.showBadge } ?: return@mapNotNull null
                val p = t.toScreen(d.label)
                MapBadge(d.id, p.x, p.y, marker.count, marker.order != null)
            }
        }
        val visible by remember(badges, badgePx) {
            derivedStateOf { visibleBadgeIds(badges, viewportState.viewport.zoom, badgePx) }
        }
        SideEffect {
            badgeHolder.badges = badges
            badgeHolder.visible = visible
        }
        val names = remember(districts) { districts.associate { it.id to it.shape.name } }
        val half = badgePx / 2f
        // Selected badges come last, so they draw on top.
        badges.sortedBy { it.selected }.forEach { badge ->
            if (badge.id !in visible) return@forEach
            key(badge.id) {
                val marker = markers.getValue(badge.id)
                val name = names[badge.id].orEmpty()
                Box(
                    Modifier
                        .offset {
                            val vp = viewportState.viewport
                            IntOffset(
                                (vp.screenX(badge.x) - half).roundToInt(),
                                (vp.screenY(badge.y) - half).roundToInt(),
                            )
                        }
                        .clearAndSetSemantics {
                            contentDescription = name
                            if (marker.pickable) {
                                onClick {
                                    onDistrictTap(badge.id)
                                    true
                                }
                            }
                        },
                ) {
                    when {
                        marker.order != null -> DistrictEventCount(
                            result = marker.order,
                            badgeColor = cs.surface,
                            textColor = cs.secondaryContainer,
                            badgeSize = badgeSize,
                        )
                        else -> DistrictEventCount(
                            result = marker.count,
                            badgeColor = if (marker.pickable) cs.primary else cs.outline,
                            badgeSize = badgeSize,
                        )
                    }
                }
            }
        }
    }
}

private class BadgeHolder {
    var badges: List<MapBadge> = emptyList()
    var visible: Set<String> = emptySet()
}

@Preview(name = "DistrictPickMap", showBackground = true, widthDp = 380, heightDp = 420)
@Composable
private fun DistrictPickMapPreview() = HackappTheme {
    DistrictPickMap(
        map = PreviewDistrictMap,
        markers = mapOf(
            "a" to DistrictMarker(14, pickable = true, showBadge = true, order = 1),
            "b" to DistrictMarker(8, pickable = true, showBadge = true, previewed = true),
            "c" to DistrictMarker(0, pickable = false, showBadge = true),
            "d" to DistrictMarker(3, pickable = false, showBadge = false),
        ),
        viewportState = rememberMapViewportState(),
        onDistrictTap = {},
        onEmptyTap = {},
    )
}
