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
 * Figma "LeaveByCard" (53:1286), at-stop screen: the Leave By time for the current Stop.
 * [nextStopName] and [transitMinutes] is the transit estimate used for Leave By, for the following Leg.
 */
@Composable
fun LeaveByCard(
    leaveByTime: String,
    nextStopName: String,
    transitMinutes: Int,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.at_stop_leave_by),
    nextStop: String = stringResource(R.string.at_stop_next, nextStopName, transitMinutes),
) {
    val color = MaterialTheme.colorScheme.onPrimaryContainer
    Column(
        modifier
            .clip(RoundedCornerShape(HackappCorners.ExtraLarge))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.fillMaxWidth())
        Text(leaveByTime, style = MaterialTheme.typography.displayMedium, color = color, modifier = Modifier.fillMaxWidth())
        Text(nextStop, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "LeaveByCard", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun LeaveByCardPreview() = HackappTheme {
    LeaveByCard(leaveByTime = "19:15", nextStopName = "Bistro du coin", transitMinutes = 10)
}
