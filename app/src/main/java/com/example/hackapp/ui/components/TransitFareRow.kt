package com.example.hackapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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

/**
 * Figma "TransitFareRow" (53:1247), UnderwayLegScreen.
 * Usage (City Pack → Compose): TransitFareRow(stringResource(R.string.leg_fare_required, fare.label))
 */
@Composable
fun TransitFareRow(fareText: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HackappIconView(HackappIcon.ConfirmationNumber, size = 22.dp, tint = MaterialTheme.colorScheme.onSurface)
        Text(fareText, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
    }
}

@Preview(name = "TransitFareRow", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = 348)
@Composable
private fun TransitFareRowPreview() = HackappTheme {
    TransitFareRow(stringResource(R.string.leg_fare_required, stringResource(R.string.leg_fare_single_ride)))
}
