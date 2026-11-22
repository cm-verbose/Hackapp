package com.example.hackapp.scaffold.screens.underway

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.components.HackappPage
import com.example.hackapp.ui.components.HackappSmallTextButton
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme

/** App bar actions and the notifications-off banner, shared by the active Underway pages. */
class UnderwayChrome(
    val showNotificationBanner: Boolean,
    val onBack: () -> Unit,
    val onEndItinerary: () -> Unit,
    val onOpenNotificationSettings: () -> Unit,
    val onDismissBanner: () -> Unit,
)

@Composable
internal fun UnderwayActivePage(
    title: String,
    chrome: UnderwayChrome,
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    bottomActions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HackappTheme.appColors
    val scheme = MaterialTheme.colorScheme
    HackappPage(
        title = title,
        modifier = modifier,
        onBack = chrome.onBack,
        containerColor = if (hero) colors.hero else scheme.surface,
        contentColor = if (hero) colors.onHeroCard else scheme.onSurface,
        actions = { UnderwayOverflowMenu(chrome.onEndItinerary) },
        bottomActions = bottomActions,
        content = {
            if (chrome.showNotificationBanner) {
                NotificationsOffBanner(chrome.onOpenNotificationSettings, chrome.onDismissBanner)
            }
            content()
        },
    )
}

@Composable
private fun UnderwayOverflowMenu(onEndItinerary: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.underway_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.end_of_day_finish)) },
                onClick = {
                    expanded = false
                    onEndItinerary()
                },
            )
        }
    }
}

/** Figma `160:2662`. */
@Composable
internal fun NotificationsOffBanner(onOpenSettings: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HackappCorners.Medium))
            .background(scheme.surfaceContainer)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = scheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.underway_notifications_off),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            HackappSmallTextButton(stringResource(R.string.underway_notifications_open_settings), onOpenSettings)
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.underway_banner_dismiss))
        }
    }
}

/** Figma `160:2635`. */
@Composable
internal fun SkipStopDialog(
    stopName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    endsItinerary: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.underway_skip_title, stopName)) },
        text = {
            Text(stringResource(if (endsItinerary) R.string.underway_skip_last_body else R.string.underway_skip_body))
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.underway_skip_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Figma `160:2649`. */
@Composable
internal fun EndEarlyDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.underway_end_early_title)) },
        text = { Text(stringResource(R.string.underway_end_early_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.underway_end_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
