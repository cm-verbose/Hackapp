package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.hackapp.ui.theme.HackappCorners
import kotlin.math.roundToInt

/*
 * Shared building blocks of DistrictMap and ItineraryRouteMap.
 *
 * In Figma both maps are placeholders: a tinted rounded box with numbered pins. Here the map
 * area is a slot (mapContent) so a maps-compose GoogleMap (District polygons, route) can be
 * dropped in later; pins are positioned by fractions of the frame, as in Figma.
 *
 * The red count circle is Eric's "DistrictEventCount". It is NOT implemented here: the maps
 * take a pinMarker slot, and DefaultMapPinMarker is only a stand-in until Eric's version exists.
 */

/** A pin whose badge is centred at ([x], [y]), fractions (0..1) of the map frame. */
@Immutable
data class MapPin(
    val id: String,
    val label: String,
    val badge: String,
    val x: Float,
    val y: Float,
    /** Muted (outline colour, 50 % opacity), e.g. a District with no matching Event. */
    val muted: Boolean = false,
)

internal val MapPinBadgeSize = 32.dp

/** Stand-in for Eric's DistrictEventCount plus its caption. Swap via the maps' pinMarker slot. */
@Composable
fun DefaultMapPinMarker(pin: MapPin, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.alpha(if (pin.muted) 0.5f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(MapPinBadgeSize)
                .background(if (pin.muted) scheme.outline else scheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(pin.badge, style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
        }
        Text(
            pin.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (pin.muted) scheme.outline else scheme.onSurface,
        )
    }
}

/** Places each marker so the centre of its badge (top [badgeSize]) sits on the pin's (x, y). */
@Composable
internal fun PinLayer(
    pins: List<MapPin>,
    onPinClick: ((MapPin) -> Unit)?,
    pinMarker: @Composable (MapPin) -> Unit,
    modifier: Modifier = Modifier,
    badgeSize: Dp = MapPinBadgeSize,
) {
    Layout(
        content = {
            pins.forEach { pin ->
                Box(if (onPinClick != null) Modifier.clickable { onPinClick(pin) } else Modifier) {
                    pinMarker(pin)
                }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val half = badgeSize.toPx() / 2
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p ->
                val pin = pins[i]
                val x = pin.x * constraints.maxWidth - p.width / 2f
                val y = pin.y * constraints.maxHeight - half
                p.place(x.roundToInt(), y.roundToInt())
            }
        }
    }
}

/** The rounded map frame (surfaceContainerHigh, corner 24) with a caption in the top-left. */
@Composable
internal fun MapFrame(
    height: Dp,
    caption: String?,
    pins: List<MapPin>,
    onPinClick: ((MapPin) -> Unit)?,
    pinMarker: @Composable (MapPin) -> Unit,
    modifier: Modifier = Modifier,
    mapContent: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(HackappCorners.Large))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        mapContent()
        PinLayer(pins, onPinClick, pinMarker, Modifier.matchParentSize())
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 10.dp),
            )
        }
    }
}
