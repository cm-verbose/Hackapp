package com.example.hackapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/**
 * Figma "LegHeader" (53:1108): the next stop on the Underway Leg screen, drawn on the dark
 * hero card. [stopTypeTag] is a slot for StopTypeTag (Eric's component).
 */
@Composable
fun LegHeader(
    stopName: String,
    stopDetails: String,
    stopTypeTag: @Composable () -> Unit,
    legTimes: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    nextStopLabel: String = stringResource(R.string.leg_next_stop_label),
) {
    val colors = HackappTheme.appColors
    Column(
        modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stopTypeTag()
            Text(nextStopLabel, style = MaterialTheme.typography.labelLarge, color = colors.onHeroCard)
        }
        Text(
            stopName,
            style = MaterialTheme.typography.displaySmall,
            color = colors.onHero,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stopDetails,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onHeroCard,
            modifier = Modifier.fillMaxWidth(),
        )
        legTimes()
    }
}

@Preview(
    name = "LegHeader",
    showBackground = true,
    backgroundColor = PreviewDefaults.HERO_CARD,
    widthDp = PreviewDefaults.WIDTH_DP,
)
@Composable
private fun LegHeaderPreview() = HackappTheme {
    LegHeader(
        stopName = "Bistro du coin",
        stopDetails = "${stringResource(R.string.stop_type_meal)} 19:30 · Le Plateau-Mont-Royal",
        stopTypeTag = { PreviewStopTypeTag(StopType.Meal, HackappIcon.Restaurant) },
        legTimes = {
            LegTimes(
                departValue = stringResource(R.string.leg_now),
                travelValue = stringResource(R.string.leg_walk_minutes, 8),
                arriveValue = "19:23",
            )
        },
    )
}
