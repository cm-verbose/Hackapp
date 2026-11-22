package com.example.hackapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma "Horizontal card": initial avatar, title, subtitle and a media area.
 * [trailing] replaces the default media placeholder. [onClick] null makes it display-only.
 * [leadingIcon] replaces the initial in the avatar disc; [subtitleMaxLines] lets a longer subtitle show.
 */
@Composable
fun HorizontalInfoCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    avatarText: String = title.take(1).uppercase(),
    leadingIcon: ImageVector? = null,
    subtitleMaxLines: Int = 2,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    /** Long press, with [onLongClickLabel] read by TalkBack as the custom action. Needs [onClick]. */
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    trailing: (@Composable () -> Unit)? = { CardMediaPlaceholder() },
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(HackappCorners.Medium)
    val border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant)
    val colors = CardDefaults.outlinedCardColors(
        containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainerLowest,
        disabledContainerColor = scheme.surfaceContainerLowest,
    )
    val selectedState = if (selected) Modifier.semantics { this.selected = true } else Modifier
    val body: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .heightIn(min = 80.dp)
                .alpha(if (enabled) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .padding(start = 16.dp)
                    .size(40.dp)
                    .background(scheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                } else {
                    Text(avatarText, style = MaterialTheme.typography.titleMedium, color = scheme.onPrimaryContainer)
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = subtitleMaxLines, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing?.invoke()
        }
    }
    if (onClick != null && onLongClick != null) {
        OutlinedCard(
            modifier = modifier
                .fillMaxWidth()
                .then(selectedState)
                .clip(shape)
                .combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = onLongClickLabel,
                ),
            shape = shape,
            colors = colors,
            border = border,
        ) { body() }
    } else if (onClick != null) {
        OutlinedCard(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().then(selectedState), shape = shape, colors = colors, border = border) { body() }
    } else {
        Card(modifier = modifier.fillMaxWidth().then(selectedState), shape = shape, colors = colors, border = border) { body() }
    }
}

/** Figma's neutral media block, used until an image is available. */
@Composable
fun CardMediaPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(80.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

@Preview(name = "HorizontalInfoCard", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun HorizontalInfoCardPreview() = HackappTheme {
    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalInfoCard(
            title = stringResource(R.string.visitor_title),
            selected = true,
            onClick = {},
        )
        HorizontalInfoCard(
            title = stringResource(R.string.resident_title),
            subtitle = stringResource(R.string.label_coming_soon),
            enabled = false,
            onClick = {},
        )
        HorizontalInfoCard(
            title = stringResource(R.string.visitor_title),
            subtitle = stringResource(R.string.label_coming_soon),
            leadingIcon = Icons.Filled.Info,
            subtitleMaxLines = 3,
            trailing = null,
        )
    }
}
