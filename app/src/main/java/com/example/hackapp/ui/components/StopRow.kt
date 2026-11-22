package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma "StopRow" (29:338): one Stop of an Itinerary in the review / timeline list:
 * time, dot, name + details, and an optional "Retirer" action.
 *
 * [dotColor] defaults to primary as in Figma; pass `HackappTheme.stopTypeColors[type]` to colour
 * it by StopType. Without [onRemove] the action is hidden (e.g. read-only timeline).
 */
@Composable
fun StopRow(
    time: String,
    stopName: String,
    details: String,
    modifier: Modifier = Modifier,
    dotColor: Color = MaterialTheme.colorScheme.primary,
    onRemove: (() -> Unit)? = null,
    removeLabel: String = stringResource(R.string.action_remove),
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(time, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 52.dp))
        Box(
            Modifier
                .size(12.dp)
                .background(dotColor, CircleShape),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stopName, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(details, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) {
            Text(
                removeLabel,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
                modifier = Modifier.clickable(role = Role.Button, onClick = onRemove),
            )
        }
    }
}

@Preview(name = "StopRow", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun StopRowPreview() = HackappTheme {
    StopRow(
        time = "18:30",
        stopName = "Jazz sur le toit",
        details = "Événement · Le Plateau-Mont-Royal",
        onRemove = {},
    )
}

@Preview(name = "StopRow · by StopType, read-only", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun StopRowTypedPreview() = HackappTheme {
    Column {
        PreviewStripStops.forEach { stop ->
            StopRow(
                time = stop.time,
                stopName = stopTypeLabel(stop.type),
                details = "Ville-Marie",
                dotColor = HackappTheme.stopTypeColors[stop.type],
            )
        }
    }
}
