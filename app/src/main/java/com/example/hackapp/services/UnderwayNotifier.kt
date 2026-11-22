package com.example.hackapp.services

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.hackapp.HackappApplication
import com.example.hackapp.MainActivity
import com.example.hackapp.R
import com.example.hackapp.domain.Itinerary
import com.example.hackapp.domain.Stop
import com.example.hackapp.domain.StopStatus
import com.example.hackapp.domain.arrived
import com.example.hackapp.domain.finishCurrent
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Local reminders for a started Itinerary:
 *  - 30 minutes after a Stop's expected start without confirmation: "Are you there?" with Arrived / Skip.
 *  - 30 minutes before a Stop's departure (not for the last Stop): "Leave in 30 min".
 * Alarms are inexact on purpose: no exact-alarm permission is needed.
 */
class UnderwayNotifier(context: Context, private val zone: ZoneId) {
    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun schedule(itinerary: Itinerary, arrivalDelayMinutes: Long = ARRIVAL_CHECK_MINUTES, leaveLeadMinutes: Long = LEAVE_LEAD_MINUTES) {
        ensureChannel(appContext)
        cancel(itinerary)
        val now = LocalDateTime.now(zone)
        itinerary.stops.forEachIndexed { i, stop ->
            val arrivalAt = stop.start.plusMinutes(arrivalDelayMinutes)
            if (arrivalAt.isAfter(now)) set(arrivalAt, intent(ACTION_ARRIVAL_CHECK, i, stop))
            val next = itinerary.stops.getOrNull(i + 1)
            val leaveAt = stop.departure.minusMinutes(leaveLeadMinutes)
            if (next != null && leaveAt.isAfter(now)) set(leaveAt, intent(ACTION_LEAVE_REMINDER, i, stop))
        }
    }

    fun cancel(itinerary: Itinerary) {
        itinerary.stops.indices.forEach { i ->
            listOf(ACTION_ARRIVAL_CHECK, ACTION_LEAVE_REMINDER).forEach { action ->
                alarms.cancel(pending(action, i, Intent(appContext, UnderwayReceiver::class.java).setAction(action), create = true))
            }
        }
        NotificationManagerCompat.from(appContext).cancelAll()
    }

    private fun set(at: LocalDateTime, pi: PendingIntent) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(zone).toInstant().toEpochMilli(), pi)
    }

    private fun intent(action: String, index: Int, subject: Stop): PendingIntent {
        val i = Intent(appContext, UnderwayReceiver::class.java).setAction(action)
            .putExtra(EXTRA_INDEX, index)
            .putExtra(EXTRA_STOP, subject.title)
            .putExtra(EXTRA_TIME, (if (action == ACTION_ARRIVAL_CHECK) subject.start else subject.departure).format(TIME))
        return pending(action, index, i, create = true)
    }

    private fun pending(action: String, index: Int, intent: Intent, create: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            index * 10 + if (action == ACTION_ARRIVAL_CHECK) 1 else 2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val ACTION_ARRIVAL_CHECK = "com.example.hackapp.ARRIVAL_CHECK"
        const val ACTION_LEAVE_REMINDER = "com.example.hackapp.LEAVE_REMINDER"
        const val ACTION_ARRIVED = "com.example.hackapp.ARRIVED"
        const val ACTION_SKIP = "com.example.hackapp.SKIP"
        const val EXTRA_INDEX = "index"
        const val EXTRA_STOP = "stop"
        const val EXTRA_TIME = "time"
        const val CHANNEL = "underway"
        const val ARRIVAL_CHECK_MINUTES = 30L
        const val LEAVE_LEAD_MINUTES = 30L
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, context.getString(R.string.notification_channel_underway), NotificationManager.IMPORTANCE_HIGH),
                )
            }
        }
    }
}

/** Posts the reminders and handles their Arrived / Skip buttons. */
class UnderwayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val index = intent.getIntExtra(UnderwayNotifier.EXTRA_INDEX, -1)
        val stop = intent.getStringExtra(UnderwayNotifier.EXTRA_STOP).orEmpty()
        val time = intent.getStringExtra(UnderwayNotifier.EXTRA_TIME).orEmpty()
        when (intent.action) {
            UnderwayNotifier.ACTION_ARRIVAL_CHECK -> post(
                context, index * 10 + 1,
                context.getString(R.string.notification_arrival_title),
                context.getString(R.string.notification_arrival_body, stop, time),
                withActions = true, index = index,
            )
            UnderwayNotifier.ACTION_LEAVE_REMINDER -> post(
                context, index * 10 + 2,
                context.getString(R.string.notification_leave_title),
                context.getString(R.string.notification_leave_body, stop, time),
                withActions = false, index = index,
            )
            UnderwayNotifier.ACTION_ARRIVED, UnderwayNotifier.ACTION_SKIP -> {
                NotificationManagerCompat.from(context).cancel(index * 10 + 1)
                val pending = goAsync()
                val app = context.applicationContext as HackappApplication
                CoroutineScope(Dispatchers.Default).launch {
                    app.underwayStore.update { p ->
                        if (p.index != index) p
                        else if (intent.action == UnderwayNotifier.ACTION_ARRIVED) p.arrived() else p.finishCurrent(StopStatus.SKIPPED)
                    }
                    pending.finish()
                }
            }
        }
    }

    private fun post(context: Context, id: Int, title: String, body: String, withActions: Boolean, index: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        UnderwayNotifier.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, UnderwayNotifier.CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (withActions) {
            builder.addAction(0, context.getString(R.string.action_arrived), button(context, UnderwayNotifier.ACTION_ARRIVED, index))
            builder.addAction(0, context.getString(R.string.action_skip_stop), button(context, UnderwayNotifier.ACTION_SKIP, index))
        }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun button(context: Context, action: String, index: Int) = PendingIntent.getBroadcast(
        context,
        index * 10 + if (action == UnderwayNotifier.ACTION_ARRIVED) 3 else 4,
        Intent(context, UnderwayReceiver::class.java).setAction(action).putExtra(UnderwayNotifier.EXTRA_INDEX, index),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
