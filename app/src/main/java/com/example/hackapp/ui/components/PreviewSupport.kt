package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.ui.theme.StopType

/*
 * Stand-ins used ONLY by @Preview functions.
 * StopTypeTag and ItineraryStrip belong to the "First Eric Group" in Figma; the Qindong components
 * take them as slots so they don't depend on that code. Swap these for Eric's components in screens.
 */

internal object PreviewDefaults {
    /** Figma frame width used for every Qindong card. */
    const val WIDTH_DP = 380
    const val CANVAS = 0xFFF0F0F1L
    const val HERO_CARD = 0xFF181A1BL
}

@Composable
internal fun previewStopTypeLabel(type: StopType): String = stringResource(
    when (type) {
        StopType.Music -> R.string.stop_type_music
        StopType.Festival -> R.string.stop_type_festival
        StopType.Theatre -> R.string.stop_type_theatre
        StopType.Comedy -> R.string.stop_type_comedy
        StopType.Exhibition -> R.string.stop_type_exhibition
        StopType.Sport -> R.string.stop_type_sport
        StopType.Cinema -> R.string.stop_type_cinema
        StopType.Family -> R.string.stop_type_family
        StopType.Meal -> R.string.stop_type_meal
        StopType.Bar -> R.string.stop_type_bar
    }
)

/** Preview stand-in for Eric's StopTypeTag (Figma 38:1026). */
@Composable
internal fun PreviewStopTypeTag(type: StopType, icon: HackappIcon) {
    val colors = HackappTheme.stopTypeColors
    Row(
        Modifier
            .background(colors[type], CircleShape)
            .padding(start = 10.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HackappIconView(icon, size = 18.dp, tint = colors.onStopType)
        Text(previewStopTypeLabel(type), style = MaterialTheme.typography.labelLarge, color = colors.onStopType)
    }
}

/** Preview stand-in for Eric's ItineraryStrip (Figma 38:1027): first stop is the current one. */
@Composable
internal fun PreviewItineraryStrip(stops: List<Pair<String, StopType>>) {
    val colors = HackappTheme.stopTypeColors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        stops.forEachIndexed { i, (time, type) ->
            if (i > 0) {
                Box(
                    Modifier
                        .weight(1f)
                        .padding(top = 9.dp, start = 4.dp, end = 4.dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    val dot = if (i == 0) 20.dp else 14.dp
                    Box(
                        Modifier
                            .size(dot)
                            .background(colors[type], CircleShape)
                            .then(if (i == 0) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                    )
                }
                Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal val PreviewStrip = listOf(
    "18:30" to StopType.Music,
    "19:30" to StopType.Meal,
    "21:00" to StopType.Comedy,
    "23:30" to StopType.Bar,
)
