package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme

@Immutable
data class ItinerarySuggestion(val icon: HackappIcon, val title: String)

/** Figma "ItinerarySuggestionsCard" (53:1226), HomeScreen: suggested itinerary themes. */
@Composable
fun ItinerarySuggestionsCard(
    suggestions: List<ItinerarySuggestion>,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.home_suggestions),
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(HackappCorners.ExtraLarge))
            .background(scheme.surfaceContainerHigh)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        suggestions.forEach { s ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HackappIconView(s.icon, size = 22.dp, tint = scheme.onSurface)
                Text(s.title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Preview(name = "ItinerarySuggestionsCard", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun ItinerarySuggestionsCardPreview() = HackappTheme {
    ItinerarySuggestionsCard(
        suggestions = listOf(
            ItinerarySuggestion(HackappIcon.MusicNote, "Soirée jazz sur le Plateau"),
            ItinerarySuggestion(HackappIcon.Restaurant, "Tournée gourmande dans Ville-Marie"),
            ItinerarySuggestion(HackappIcon.People, "Dimanche en famille à Rosemont"),
        ),
    )
}
