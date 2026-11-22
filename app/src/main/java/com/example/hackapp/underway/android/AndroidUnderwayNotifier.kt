package com.example.hackapp.underway.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.hackapp.R
import com.example.hackapp.ui.components.formatSentenceTime
import com.example.hackapp.underway.AlertKind
import com.example.hackapp.underway.PlannedAlert
import com.example.hackapp.underway.UnderwayNotifier
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Notifications with the Stop name and time, visible on the lock screen. Text is from resources. */
class AndroidUnderwayNotifier(context: Context) : UnderwayNotifier {
    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)
    private val shownIds = appContext.getSharedPreferences("underway_notifications", Context.MODE_PRIVATE)

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        fun channel(id: String, name: Int, importance: Int) = NotificationChannel(
            id, appContext.getString(name), importance,
        ).apply { lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC }
        val system = appContext.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(channel(CH_ARRIVAL, R.string.notification_channel_arrival, NotificationManager.IMPORTANCE_DEFAULT))
        system.createNotificationChannel(channel(CH_LEAVE, R.string.notification_channel_leave, NotificationManager.IMPORTANCE_HIGH))
        system.createNotificationChannel(channel(CH_LATE, R.string.notification_channel_late, NotificationManager.IMPORTANCE_DEFAULT))
    }

    @android.annotation.SuppressLint("MissingPermission")
    override fun show(uid: String, itineraryId: String, alert: PlannedAlert) {
        if (!manager.areNotificationsEnabled()) return
        ensureChannels()
        val locale = Locale.getDefault()
        val value = DateTimeFormatter.ofPattern("HH:mm", locale)
        val (channel, title, body) = when (alert.kind) {
            AlertKind.ArrivalReminder -> Triple(
                CH_ARRIVAL,
                appContext.getString(R.string.notification_arrival_title),
                appContext.getString(R.string.notification_arrival_body, alert.stopName, value.format(alert.stopStart)),
            )
            AlertKind.LeaveBy -> Triple(
                CH_LEAVE,
                appContext.getString(
                    R.string.notification_leave_title,
                    formatSentenceTime(alert.leaveBy?.toLocalTime() ?: alert.stopStart.toLocalTime(), locale),
                ),
                appContext.getString(R.string.notification_leave_body, alert.nextStopName.orEmpty()),
            )
            AlertKind.LateWarning -> Triple(
                CH_LATE,
                appContext.getString(R.string.notification_late_title),
                appContext.getString(
                    R.string.notification_late_body, alert.stopName, formatSentenceTime(alert.stopStart.toLocalTime(), locale),
                ),
            )
        }
        val open = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
        val builder = NotificationCompat.Builder(appContext, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (alert.highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        open?.let {
            builder.setContentIntent(PendingIntent.getActivity(appContext, 0, it, PendingIntent.FLAG_IMMUTABLE))
        }
        when (alert.kind) {
            AlertKind.ArrivalReminder -> {
                builder.addAction(0, appContext.getString(R.string.action_arrived), action(uid, itineraryId, UnderwayAndroid.ACTION_ARRIVED, alert.stopId, alert.id))
            }
            AlertKind.LeaveBy -> {
                builder.addAction(0, appContext.getString(R.string.action_leaving), action(uid, itineraryId, UnderwayAndroid.ACTION_LEAVING, alert.stopId, alert.id))
            }
            AlertKind.LateWarning -> {
                builder.addAction(0, appContext.getString(R.string.action_go_anyway), action(uid, itineraryId, UnderwayAndroid.ACTION_GO_ANYWAY, alert.stopId, alert.id))
            }
        }
        if (alert.kind == AlertKind.LeaveBy) {
            open?.let {
                builder.addAction(0, appContext.getString(R.string.notification_action_open), PendingIntent.getActivity(appContext, 1, it, PendingIntent.FLAG_IMMUTABLE))
            }
        } else {
            alert.skipStopId?.let {
                builder.addAction(0, appContext.getString(R.string.action_skip_stop), action(uid, itineraryId, UnderwayAndroid.ACTION_SKIP, it, alert.id))
            }
        }
        val id = notificationId(itineraryId, alert.id)
        manager.notify(id, builder.build())
        shownIds.edit().putStringSet(itineraryId, shownIds.getStringSet(itineraryId, emptySet()).orEmpty() + id.toString()).apply()
    }

    override fun cancelAll(itineraryId: String) {
        shownIds.getStringSet(itineraryId, emptySet()).orEmpty().forEach { manager.cancel(it.toInt()) }
        shownIds.edit().remove(itineraryId).apply()
    }

    private fun action(uid: String, itineraryId: String, action: String, stopId: String, alertId: String): PendingIntent {
        val intent = Intent(appContext, UnderwayActionReceiver::class.java)
            .setAction(action)
            .setData(android.net.Uri.parse("underway://action/${android.net.Uri.encode(itineraryId)}/${android.net.Uri.encode(alertId)}/$action"))
            .putExtra(UnderwayAndroid.EXTRA_UID, uid)
            .putExtra(UnderwayAndroid.EXTRA_ITINERARY, itineraryId)
            .putExtra(UnderwayAndroid.EXTRA_STOP, stopId)
        return PendingIntent.getBroadcast(appContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun notificationId(itineraryId: String, alertId: String) = (itineraryId + alertId).hashCode()

    private companion object {
        const val CH_ARRIVAL = "underway_arrival"
        const val CH_LEAVE = "underway_leave"
        const val CH_LATE = "underway_late"
    }
}
