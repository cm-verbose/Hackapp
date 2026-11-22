package com.example.hackapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma "LegTimes" (53:1090): Depart · Travel · Arrive, shown on the dark hero card.
 * Values are data (times, walk estimate); labels come from strings.xml. A null [departValue]
 * leaves Depart out, for a Leg that shows it as a [DepartureChoice] instead.
 */
@Composable
fun LegTimes(
    departValue: String?,
    travelValue: String,
    arriveValue: String,
    modifier: Modifier = Modifier,
    departLabel: String = stringResource(R.string.leg_depart),
    travelLabel: String = stringResource(R.string.leg_travel),
    arriveLabel: String = stringResource(R.string.leg_arrive),
    color: Color = HackappTheme.appColors.onHeroCard,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        departValue?.let { LegTimeColumn(departLabel, it, color) }
        LegTimeColumn(travelLabel, travelValue, color)
        LegTimeColumn(arriveLabel, arriveValue, color)
    }
}

@Composable
private fun LegTimeColumn(label: String, value: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(name = "LegTimes", showBackground = true, backgroundColor = PreviewDefaults.HERO_CARD, widthDp = 364)
@Composable
private fun LegTimesPreview() = HackappTheme {
    LegTimes(
        departValue = stringResource(R.string.leg_now),
        travelValue = stringResource(R.string.leg_walk_minutes, 8),
        arriveValue = "19:23",
    )
}
