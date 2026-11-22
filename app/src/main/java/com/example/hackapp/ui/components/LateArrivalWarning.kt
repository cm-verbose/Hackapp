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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma "LateArrivalWarning" (53:1297): shown when the next Stop can no longer be reached on time.
 * There is no automatic replanning: the user goes anyway or skips the Stop.
 */
@Composable
fun LateArrivalWarning(
    stopName: String,
    onGoAnyway: () -> Unit,
    onSkipStop: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.late_warning, stopName),
    detail: String = stringResource(R.string.late_no_replanning),
    goAnywayLabel: String = stringResource(R.string.action_go_anyway),
    skipStopLabel: String = stringResource(R.string.action_skip_stop),
) {
    val color = MaterialTheme.colorScheme.onErrorContainer
    Column(
        modifier
            .clip(RoundedCornerShape(HackappCorners.Large))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(message, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.fillMaxWidth())
        Text(detail, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HackappSmallButton(text = goAnywayLabel, onClick = onGoAnyway)
            HackappSmallTextButton(text = skipStopLabel, onClick = onSkipStop)
        }
    }
}

@Preview(name = "LateArrivalWarning", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun LateArrivalWarningPreview() = HackappTheme {
    LateArrivalWarning(stopName = "Bistro du coin", onGoAnyway = {}, onSkipStop = {})
}
