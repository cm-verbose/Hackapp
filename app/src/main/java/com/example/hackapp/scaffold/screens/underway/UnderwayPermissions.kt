package com.example.hackapp.scaffold.screens.underway

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.example.hackapp.R

/**
 * The permissions asked for at Start. Neither ever blocks it: a refusal only means normal
 * alerts, or none.
 *
 * Start flow: call [requestNotifications], then `coordinator.start(...)`. On
 * `Started(view, askExactAlarm = true)` call [offerExactAlarm] and navigate from the `onClosed` of
 * [UnderwayExactAlarmRationale]. Otherwise navigate at once.
 */
@Stable
class UnderwayStartPermissions internal constructor(
    private val context: Context,
    private val launchNotifications: () -> Unit,
    showRationale: androidx.compose.runtime.MutableState<Boolean>,
) {
    internal var rationaleVisible by showRationale

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launchNotifications()
    }

    /** Shows the rationale. False when this Android version has no such permission to ask for. */
    fun offerExactAlarm(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        rationaleVisible = true
        return true
    }

    internal fun openExactAlarmSettings() {
        rationaleVisible = false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        context.startSafely(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
        )
    }
}

@Composable
fun rememberUnderwayStartPermissions(): UnderwayStartPermissions {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val rationale = rememberSaveable { mutableStateOf(false) }
    return remember(context, launcher) {
        UnderwayStartPermissions(
            context = context,
            launchNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            showRationale = rationale,
        )
    }
}

/**
 * Place in the Start screen. [onClosed] runs after any answer: navigate to Underway there, because
 * leaving the screen earlier would remove this dialog before it is seen.
 */
@Composable
fun UnderwayExactAlarmRationale(permissions: UnderwayStartPermissions, onClosed: () -> Unit) {
    if (!permissions.rationaleVisible) return
    val decline = {
        permissions.rationaleVisible = false
        onClosed()
    }
    AlertDialog(
        onDismissRequest = decline,
        text = { Text(stringResource(R.string.underway_exact_alarm_rationale)) },
        confirmButton = {
            TextButton(
                onClick = {
                    permissions.openExactAlarmSettings()
                    onClosed()
                },
            ) {
                Text(stringResource(R.string.underway_exact_alarm_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = decline) {
                Text(stringResource(R.string.underway_exact_alarm_later))
            }
        },
    )
}

/** Opens this app's notification settings, from the notifications-off banner. */
internal fun Context.openNotificationSettings() {
    startSafely(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
    )
}

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Settings page missing on this device: alerts simply stay as they are.
    }
}
