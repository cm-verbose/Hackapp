package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma "StopRow" (29:338): one Stop of an Itinerary in the review / timeline list:
 * time, dot, name + details, and an optional "Retirer" action.
 *
 * [dotColor] defaults to primary as in Figma; pass `HackappTheme.stopTypeColors[type]` to colour
 * it by StopType. Without [onRemove] the action is hidden (e.g. read-only timeline).
 * With [onClick] the row can be selected, and [highlighted] shows that by colour only.
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
    removeEnabled: Boolean = true,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val selection = if (onClick == null) {
        Modifier
    } else {
        val shape = RoundedCornerShape(HackappCorners.Medium)
        Modifier
            .clip(shape)
            .background(if (highlighted) scheme.primaryContainer else Color.Transparent, shape)
            .selectable(selected = highlighted, onClick = onClick)
            .padding(horizontal = 8.dp)
    }
    Row(
        modifier
            .fillMaxWidth()
            .then(selection)
            // The 48 dp Remove target supplies the rest of the row's height.
            .padding(vertical = if (onRemove == null) 12.dp else 8.dp),
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
            val removeDescription = stringResource(R.string.strip_stop, removeLabel, stopName)
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = removeDescription }
                    .clickable(enabled = removeEnabled, role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    removeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (removeEnabled) scheme.primary else scheme.onSurface.copy(alpha = 0.38f),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
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
