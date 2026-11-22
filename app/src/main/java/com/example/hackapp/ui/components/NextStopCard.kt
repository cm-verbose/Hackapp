package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
 * Figma "NextStopCard" (53:1479), underway screen: the next Stop, its position in the Itinerary
 * ([stopIndex] is 1-based) and a details line (start time, where the Leg departs from).
 */
@Composable
fun NextStopCard(
    stopName: String,
    stopIndex: Int,
    stopCount: Int,
    stopDetails: String,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.leg_stop_progress, stopIndex, stopCount),
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(HackappCorners.Large))
            .background(scheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        Text(stopName, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, modifier = Modifier.fillMaxWidth())
        Text(stopDetails, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "NextStopCard", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun NextStopCardPreview() = HackappTheme {
    NextStopCard(
        stopName = "Jazz sur le toit",
        stopIndex = 1,
        stopCount = 4,
        stopDetails = stringResource(R.string.leg_start_from_location, "18:30"),
    )
}
