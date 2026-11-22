package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma "LocalNotificationPreview" (30:743): an in-app mock of an Android local notification
 * (header, title, body, up to two actions). Not the real notification: use it to preview what the
 * user will receive (e.g. on the review screen or in onboarding). Real notifications use the
 * `notification_*` strings with NotificationCompat.
 */
@Composable
fun LocalNotificationPreview(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    primaryAction: String? = null,
    secondaryAction: String? = null,
    onPrimaryAction: () -> Unit = {},
    onSecondaryAction: () -> Unit = {},
    header: String = stringResource(R.string.notification_preview_header, stringResource(R.string.app_name)),
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(HackappCorners.Large)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(3.dp, shape)
            .background(scheme.surfaceContainerHighest, shape)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(header, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, modifier = Modifier.fillMaxWidth())
        Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        if (primaryAction != null || secondaryAction != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                primaryAction?.let { ActionText(it, onPrimaryAction) }
                secondaryAction?.let { ActionText(it, onSecondaryAction) }
            }
        }
    }
}

@Composable
private fun ActionText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Preview(name = "LocalNotificationPreview · arrival", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun LocalNotificationArrivalPreview() = HackappTheme {
    LocalNotificationPreview(
        title = stringResource(R.string.notification_arrival_title),
        body = stringResource(R.string.notification_arrival_body, "Jazz sur le toit", "18:30"),
        primaryAction = stringResource(R.string.action_arrived),
        secondaryAction = stringResource(R.string.action_skip_stop),
        modifier = Modifier.padding(8.dp),
    )
}

@Preview(name = "LocalNotificationPreview · leave by", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun LocalNotificationLeavePreview() = HackappTheme {
    LocalNotificationPreview(
        title = stringResource(R.string.notification_leave_title),
        body = stringResource(R.string.notification_leave_body, "Jazz sur le toit", "19:15"),
        primaryAction = stringResource(R.string.action_leaving),
        modifier = Modifier.padding(8.dp),
    )
}
