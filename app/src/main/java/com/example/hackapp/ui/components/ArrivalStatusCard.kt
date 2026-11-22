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
 * Figma "ArrivalStatusCard" (53:1470), en-route screen: expected arrival at the next Stop.
 * Arrival is never detected by GPS; the user confirms it ([hint]).
 */
@Composable
fun ArrivalStatusCard(
    expectedArrivalTime: String,
    modifier: Modifier = Modifier,
    status: String = stringResource(R.string.arrival_en_route),
    expectedArrival: String = stringResource(R.string.arrival_expected, expectedArrivalTime),
    hint: String = stringResource(R.string.arrival_not_detected_note),
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(HackappCorners.Large))
            .background(scheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(status, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        Text(expectedArrival, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface, modifier = Modifier.fillMaxWidth())
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "ArrivalStatusCard", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun ArrivalStatusCardPreview() = HackappTheme {
    ArrivalStatusCard(expectedArrivalTime = "18:22")
}
