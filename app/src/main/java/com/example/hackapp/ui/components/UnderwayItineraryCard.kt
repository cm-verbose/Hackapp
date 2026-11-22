package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma "UnderwayItineraryCard" (53:1147), HomeScreen: the itinerary in progress, its stops
 * strip and the resume action. [itineraryStrip] is a slot, usually ItineraryStrip.
 *
 * Note: in Figma the white strip container is clipped to 10 dp, which cuts the time labels;
 * here it wraps its content so the labels stay readable.
 */
@Composable
fun UnderwayItineraryCard(
    itineraryName: String,
    nextStop: String,
    onResume: () -> Unit,
    itineraryStrip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.home_underway_label),
    resumeLabel: String = stringResource(R.string.home_resume_itinerary),
) {
    val colors = HackappTheme.appColors
    Column(
        modifier
            .clip(RoundedCornerShape(HackappCorners.ExtraLarge))
            .background(colors.hero)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onHeroCard)
        Text(itineraryName, style = MaterialTheme.typography.headlineMedium, color = colors.onHero)
        Text(nextStop, style = MaterialTheme.typography.bodyLarge, color = colors.onHeroCard)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(HackappCorners.Large))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) { itineraryStrip() }
        HackappSmallButton(text = resumeLabel, onClick = onResume)
    }
}

@Preview(name = "UnderwayItineraryCard", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun UnderwayItineraryCardPreview() = HackappTheme {
    UnderwayItineraryCard(
        itineraryName = "Soirée sur le Plateau",
        nextStop = stringResource(R.string.home_next_stop, "19:30", "Bistro du coin"),
        onResume = {},
        itineraryStrip = { ItineraryStrip(PreviewStripStops) },
    )
}
