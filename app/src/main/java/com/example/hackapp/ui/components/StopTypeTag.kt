package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.ui.theme.StopType

/** Icon of each stop type, as in Figma "StopTypeTag" (38:1026). */
val StopType.icon: HackappIcon
    get() = when (this) {
        StopType.Music -> HackappIcon.MusicNote
        StopType.Festival -> HackappIcon.Star
        StopType.Theatre -> HackappIcon.LocalActivity
        StopType.Comedy -> HackappIcon.SentimentSatisfied
        StopType.Exhibition -> HackappIcon.Palette
        StopType.Sport -> HackappIcon.DirectionsRun
        StopType.Cinema -> HackappIcon.Movie
        StopType.Family -> HackappIcon.People
        StopType.Meal -> HackappIcon.Restaurant
        StopType.Bar -> HackappIcon.LocalBar
    }

/** Localized label of a stop type (strings.xml `stop_type_*`). */
@Composable
fun stopTypeLabel(type: StopType): String = stringResource(
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

/**
 * Figma "StopTypeTag" (38:1026): pill with the stop type's icon and label, filled with the
 * StopType colour of the active palette (`HackappTheme.stopTypeColors`). 10 variants, one per [StopType].
 */
@Composable
fun StopTypeTag(
    type: StopType,
    modifier: Modifier = Modifier,
    label: String = stopTypeLabel(type),
) {
    val colors = HackappTheme.stopTypeColors
    Row(
        modifier
            .background(colors[type], CircleShape)
            .padding(start = 10.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HackappIconView(type.icon, size = 18.dp, tint = colors.onStopType)
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onStopType, maxLines = 1)
    }
}

@Preview(name = "StopTypeTag · Music", showBackground = true)
@Composable
private fun StopTypeTagPreview() = HackappTheme {
    StopTypeTag(StopType.Music, Modifier.padding(8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "StopTypeTag · all types", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun StopTypeTagAllPreview() = HackappTheme {
    FlowRow(
        Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StopType.entries.forEach { StopTypeTag(it) }
    }
}
