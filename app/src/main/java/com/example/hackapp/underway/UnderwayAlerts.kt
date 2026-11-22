package com.example.hackapp.underway

/** Schedules the alerts the engine plans. The Android version uses AlarmManager. */
interface UnderwayAlertScheduler {
    /** True when alerts can be exact. When false they fall back to normal alerts. */
    fun canScheduleExact(): Boolean

    /** Makes [alerts] the only pending alerts of this Itinerary. */
    fun replaceAll(uid: String, itineraryId: String, alerts: List<PlannedAlert>)

    fun cancelAll(itineraryId: String)
}

/** Shows and removes the notifications of fired alerts. */
interface UnderwayNotifier {
    fun show(uid: String, itineraryId: String, alert: PlannedAlert)
    fun cancelAll(itineraryId: String)
}

/** Records what was scheduled and shown. For tests and previews. */
class FakeUnderwayAlertScheduler(var exactAllowed: Boolean = true) : UnderwayAlertScheduler {
    val pending = mutableMapOf<String, List<PlannedAlert>>()
    var replaceCount = 0
        private set

    override fun canScheduleExact() = exactAllowed

    override fun replaceAll(uid: String, itineraryId: String, alerts: List<PlannedAlert>) {
        replaceCount++
        pending[itineraryId] = alerts
    }

    override fun cancelAll(itineraryId: String) {
        pending.remove(itineraryId)
    }

    fun alertsFor(itineraryId: String): List<PlannedAlert> = pending[itineraryId].orEmpty()
}

class FakeUnderwayNotifier : UnderwayNotifier {
    val shown = mutableListOf<PlannedAlert>()
    var cancelCount = 0
        private set

    override fun show(uid: String, itineraryId: String, alert: PlannedAlert) {
        shown += alert
    }

    override fun cancelAll(itineraryId: String) {
        cancelCount++
    }
}
