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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.ui.theme.StopType

/** One Stop on the [ItineraryStrip]: its start time and type (drives the dot colour). */
@Immutable
data class StripStop(val time: String, val type: StopType)

private val CurrentDot = 20.dp
private val OtherDot = 14.dp

/**
 * Figma "ItineraryStrip" (38:1027): the Stops of the Underway Itinerary as dots coloured by
 * StopType, joined by outline-variant lines. The Stop at [currentIndex] is
 * larger and outlined; null highlights none.
 */
@Composable
fun ItineraryStrip(
    stops: List<StripStop>,
    modifier: Modifier = Modifier,
    currentIndex: Int? = null,
) {
    val colors = HackappTheme.stopTypeColors
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        stops.forEachIndexed { i, stop ->
            if (i > 0) {
                Box(
                    Modifier
                        .weight(1f)
                        .padding(top = CurrentDot / 2 - 1.dp, start = 4.dp, end = 4.dp)
                        .height(2.dp)
                        .background(scheme.outlineVariant),
                )
            }
            val current = i == currentIndex
            val description = stringResource(
                if (current) R.string.strip_stop_current else R.string.strip_stop,
                stop.time,
                stopTypeLabel(stop.type),
            )
            Column(
                Modifier.clearAndSetSemantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(CurrentDot), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(if (current) CurrentDot else OtherDot)
                            .background(colors[stop.type], CircleShape)
                            .then(if (current) Modifier.border(1.5.dp, scheme.onSurface, CircleShape) else Modifier),
                    )
                }
                Text(stop.time, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

internal val PreviewStripStops = listOf(
    StripStop("18:30", StopType.Music),
    StripStop("19:30", StopType.Meal),
    StripStop("21:00", StopType.Comedy),
    StripStop("23:30", StopType.Bar),
)

@Preview(name = "ItineraryStrip", showBackground = true, widthDp = 340)
@Composable
private fun ItineraryStripPreview() = HackappTheme {
    ItineraryStrip(PreviewStripStops)
}

@Preview(name = "ItineraryStrip · current = 2", showBackground = true, widthDp = 340)
@Composable
private fun ItineraryStripCurrentPreview() = HackappTheme {
    ItineraryStrip(PreviewStripStops, currentIndex = 2)
}
