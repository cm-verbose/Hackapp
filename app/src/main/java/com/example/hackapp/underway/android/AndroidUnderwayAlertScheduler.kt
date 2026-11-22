package com.example.hackapp.underway.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.hackapp.underway.PlannedAlert
import com.example.hackapp.underway.UnderwayAlertScheduler

/**
 * Exact alarms when allowed, otherwise normal alarms that still fire while the device idles.
 * Alert ids are remembered per Itinerary so they can be cancelled after a restart.
 */
class AndroidUnderwayAlertScheduler(context: Context) : UnderwayAlertScheduler {
    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = appContext.getSharedPreferences("underway_alarms", Context.MODE_PRIVATE)

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    override fun replaceAll(uid: String, itineraryId: String, alerts: List<PlannedAlert>) {
        cancelAll(itineraryId)
        val exact = canScheduleExact()
        alerts.forEach { alert ->
            val pending = pendingIntent(uid, itineraryId, alert.id)
            val at = alert.at.toEpochMilli()
            try {
                if (exact) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                } else {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                }
            } catch (_: SecurityException) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }
        prefs.edit().putStringSet(itineraryId, alerts.map { it.id }.toSet()).putString("uid_$itineraryId", uid).apply()
    }

    override fun cancelAll(itineraryId: String) {
        val uid = prefs.getString("uid_$itineraryId", null) ?: return
        prefs.getStringSet(itineraryId, emptySet()).orEmpty().forEach { id ->
            alarms.cancel(pendingIntent(uid, itineraryId, id))
        }
        prefs.edit().remove(itineraryId).remove("uid_$itineraryId").apply()
    }

    private fun pendingIntent(uid: String, itineraryId: String, alertId: String): PendingIntent {
        val intent = Intent(appContext, UnderwayAlarmReceiver::class.java)
            .setAction(UnderwayAndroid.ACTION_ALARM)
            .setData(Uri.parse("underway://alarm/${Uri.encode(itineraryId)}/${Uri.encode(alertId)}"))
            .putExtra(UnderwayAndroid.EXTRA_UID, uid)
            .putExtra(UnderwayAndroid.EXTRA_ITINERARY, itineraryId)
        return PendingIntent.getBroadcast(
            appContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
