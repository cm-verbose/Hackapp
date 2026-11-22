package com.example.hackapp.underway.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.hackapp.HackappApplication
import com.example.hackapp.underway.UnderwayCoordinator
import com.example.hackapp.underway.UnderwayEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object UnderwayAndroid {
    const val ACTION_ALARM = "com.example.hackapp.underway.ALARM"
    const val ACTION_ARRIVED = "com.example.hackapp.underway.ARRIVED"
    const val ACTION_LEAVING = "com.example.hackapp.underway.LEAVING"
    const val ACTION_SKIP = "com.example.hackapp.underway.SKIP"
    const val ACTION_GO_ANYWAY = "com.example.hackapp.underway.GO_ANYWAY"
    const val EXTRA_UID = "uid"
    const val EXTRA_ITINERARY = "itinerary"
    const val EXTRA_STOP = "stop"
}

private fun Context.coordinator(): UnderwayCoordinator =
    (applicationContext as HackappApplication).underwayCoordinator

/** Runs [block] after the receiver returns, then releases it. */
private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("Underway", "Receiver work failed", e)
        } finally {
            pending.finish()
        }
    }
}

/** An alarm for an alert: fires it and reschedules the rest. */
class UnderwayAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uid = intent.getStringExtra(UnderwayAndroid.EXTRA_UID) ?: return
        val itinerary = intent.getStringExtra(UnderwayAndroid.EXTRA_ITINERARY) ?: return
        async { context.coordinator().onAlarm(uid, itinerary) }
    }
}

/** Notification buttons: they update the state and never open the app. */
class UnderwayActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uid = intent.getStringExtra(UnderwayAndroid.EXTRA_UID) ?: return
        val itinerary = intent.getStringExtra(UnderwayAndroid.EXTRA_ITINERARY) ?: return
        val stop = intent.getStringExtra(UnderwayAndroid.EXTRA_STOP)
        val event = when (intent.action) {
            UnderwayAndroid.ACTION_ARRIVED -> UnderwayEvent.ConfirmArrival(stop)
            UnderwayAndroid.ACTION_LEAVING -> UnderwayEvent.Leaving(stop)
            UnderwayAndroid.ACTION_SKIP -> UnderwayEvent.SkipStop(stop)
            UnderwayAndroid.ACTION_GO_ANYWAY -> UnderwayEvent.GoAnyway(stop ?: return)
            else -> return
        }
        async { context.coordinator().dispatch(uid, itinerary, event) }
    }
}

private val BootActions = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
)

/** After a reboot, an app update or a clock change, alarms are gone or wrong. */
class UnderwayBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BootActions) return
        val app = context.applicationContext as HackappApplication
        async { app.underwayCoordinator.onBoot(app.currentUid()) }
    }
}
