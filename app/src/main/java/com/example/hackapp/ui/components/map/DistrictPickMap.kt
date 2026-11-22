package com.example.hackapp.ui.components.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.citypack.DistrictMapData
import com.example.hackapp.components.DistrictEventCount
import com.example.hackapp.ui.theme.HackappTheme
import kotlin.math.roundToInt

/** How one District appears on the pick map. [order] is its visit position when selected. */
data class DistrictMarker(
    val count: Int,
    val pickable: Boolean,
    val showLabel: Boolean,
    val order: Int? = null,
    val previewed: Boolean = false,
)

private val LabelMaxWidth = 104.dp
private val LabelPaddingX = 4.dp
private val LabelPaddingY = 2.dp
private const val LABEL_MAX_LINES = 3

private val BadgeSize = 24.dp

private class PlacedName(
    val label: MapLabel,
    val layout: TextLayoutResult,
    val marker: DistrictMarker,
)

/**
 * Full-size interactive District map. Below [MAP_NAMES_ZOOM] a District shows its count badge, or
 * nothing when it has no Events. From that zoom it shows its upright name. Markers move in the
 * layout phase and recompose only when what is shown changes.
 */
@Composable
fun DistrictPickMap(
    map: DistrictMapData,
    markers: Map<String, DistrictMarker>,
    viewportState: MapViewportState,
    onDistrictTap: (String) -> Unit,
    onEmptyTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val currentMarkers by rememberUpdatedState(markers)
    val measurer = rememberTextMeasurer(cacheSize = 96)
    val textStyle = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Center)
    val maxWidthPx = with(density) { LabelMaxWidth.roundToPx() }
    val padX = with(density) { LabelPaddingX.toPx() }
    val padY = with(density) { LabelPaddingY.toPx() }
    val badgePx = with(density) { BadgeSize.toPx() }
    // Published after composition for the tap handler, which runs outside it.
    val labelHolder = remember { LabelHolder() }

    CityMap(
        map = map,
        modifier = modifier.fillMaxSize().clipToBounds(),
        viewportState = viewportState,
        fillOf = { d ->
            val marker = currentMarkers[d.id]
            when {
                marker?.order != null -> cs.secondaryContainer
                marker?.previewed == true -> cs.primary
                marker?.pickable == true -> cs.primaryContainer
                else -> cs.outlineVariant.copy(alpha = 0.55f)
            }
        },
        tapTarget = { tap ->
            labelAt(labelHolder.labels, labelHolder.visible, viewportState.viewport, tap.x, tap.y)
        },
        onDistrictTap = { id -> if (currentMarkers[id]?.pickable == true) onDistrictTap(id) },
        onEmptyTap = onEmptyTap,
    ) { t, districts ->
        val showNames by remember { derivedStateOf { viewportState.viewport.zoom >= MAP_NAMES_ZOOM } }
        val names = remember(districts, t.scale, t.origin, markers, textStyle, maxWidthPx) {
            districts.mapNotNull { d ->
                val marker = markers[d.id]?.takeIf { it.showLabel } ?: return@mapNotNull null
                val text = buildString {
                    marker.order?.let { append(it).append("\u00a0· ") }
                    append(breakableName(d.shape.name))
                    if (marker.count > 0) append("\u00a0· ").append(marker.count)
                }
                val layout = measurer.measure(
                    text = text,
                    style = textStyle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = LABEL_MAX_LINES,
                    constraints = Constraints(maxWidth = maxWidthPx),
                )
                val p = t.toScreen(d.label)
                PlacedName(
                    label = MapLabel(
                        id = d.id,
                        x = p.x,
                        y = p.y,
                        width = layout.size.width + padX * 2f,
                        height = layout.size.height + padY * 2f,
                        count = marker.count,
                        pinned = marker.order != null || marker.previewed,
                    ),
                    layout = layout,
                    marker = marker,
                )
            }
        }
        val shown = remember(names, showNames, badgePx) {
            names.mapNotNull { placed ->
                when {
                    showNames -> placed to placed.label
                    placed.marker.order == null && placed.marker.count == 0 -> null
                    else -> placed to placed.label.copy(width = badgePx, height = badgePx)
                }
            }
        }
        val labels = remember(shown) { shown.map { it.second } }
        val visible by remember(labels) {
            derivedStateOf(structuralEqualityPolicy()) { visibleLabelIds(labels, viewportState.viewport) }
        }
        SideEffect {
            labelHolder.labels = labels
            labelHolder.visible = visible
        }
        val districtNames = remember(districts) { districts.associate { it.id to it.shape.name } }
        // Pinned markers come last, so they draw on top.
        shown.sortedBy { it.second.pinned }.forEach { (placed, label) ->
            val marker = placed.marker
            // A marker hidden by overlap keeps an invisible node, so TalkBack can still reach the District.
            val hidden = label.id !in visible
            if (hidden && !marker.pickable) return@forEach
            key(label.id) {
                val showsName = showNames && !hidden
                val background = when {
                    marker.order != null -> cs.secondaryContainer
                    marker.previewed -> cs.primary
                    else -> Color.Transparent
                }
                val textColor = when {
                    marker.order != null -> cs.onSecondaryContainer
                    marker.previewed -> cs.onPrimary
                    marker.pickable -> cs.onSurface
                    else -> cs.onSurfaceVariant
                }
                val halo = if (label.pinned) null else Shadow(cs.surface, blurRadius = 6f)
                val name = districtNames[label.id].orEmpty()
                val description = when {
                    marker.order != null -> pluralStringResource(R.plurals.map_district_selected, marker.count, name, marker.order, marker.count)
                    !marker.pickable -> stringResource(R.string.map_district_unavailable, name)
                    else -> pluralStringResource(R.plurals.map_district_description, marker.count, name, marker.count)
                }
                Box(
                    Modifier
                        .offset {
                            val vp = viewportState.viewport
                            IntOffset(
                                labelStart(vp.screenX(label.x, label.y), label.width, viewportState.canvasWidth).roundToInt(),
                                labelStart(vp.screenY(label.x, label.y), label.height, viewportState.canvasHeight).roundToInt(),
                            )
                        }
                        .size(with(density) { label.width.toDp() }, with(density) { label.height.toDp() })
                        .then(
                            if (!showsName) Modifier
                            else Modifier
                                .background(background, RoundedCornerShape(4.dp))
                                .drawBehind {
                                    drawText(placed.layout, color = textColor, topLeft = Offset(padX, padY), shadow = halo)
                                },
                        )
                        .clearAndSetSemantics {
                            contentDescription = description
                            if (marker.pickable) {
                                onClick {
                                    onDistrictTap(label.id)
                                    true
                                }
                            }
                        },
                ) {
                    if (!showsName && !hidden) {
                        if (marker.order != null) {
                            DistrictEventCount(
                                result = marker.order,
                                badgeColor = cs.surface,
                                textColor = cs.secondaryContainer,
                                badgeSize = BadgeSize,
                            )
                        } else {
                            DistrictEventCount(result = marker.count, badgeSize = BadgeSize)
                        }
                    }
                }
            }
        }
    }
}

/** Lets a long name wrap after its hyphens instead of inside a word. */
private fun breakableName(name: String) = name.replace("-", "-\u200B").replace("–", "–\u200B")

private class LabelHolder {
    var labels: List<MapLabel> = emptyList()
    var visible: Set<String> = emptySet()
}

@Preview(name = "DistrictPickMap", showBackground = true, widthDp = 380, heightDp = 420)
@Composable
private fun DistrictPickMapPreview() = HackappTheme {
    DistrictPickMap(
        map = PreviewDistrictMap,
        markers = mapOf(
            "a" to DistrictMarker(14, pickable = true, showLabel = true, order = 1),
            "b" to DistrictMarker(8, pickable = true, showLabel = true, previewed = true),
            "c" to DistrictMarker(0, pickable = false, showLabel = true),
            "d" to DistrictMarker(3, pickable = false, showLabel = false),
        ),
        viewportState = rememberMapViewportState(),
        onDistrictTap = {},
        onEmptyTap = {},
    )
}
