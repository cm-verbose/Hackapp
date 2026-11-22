package com.example.hackapp.underway

import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.StopKind

/** An Event needs at least this share of its expected time. The boundary counts. */
const val MIN_STAY_PERCENT = 20L

/** The end of the stay: leaving, else the next user action, else the end of the day. */
internal fun stayEndMillis(record: StopRecord, state: UnderwayState): Long? =
    record.leftAtMillis ?: record.nextActionAtMillis ?: state.endedAtMillis

/**
 * Meals need a confirmed arrival. Events need [MIN_STAY_PERCENT] of the expected period, counted
 * as the overlap of the stay with it. The period is `Stop.start` to Planned Departure for every Event: Selection sets `start` to the arrival for a drop-in. Time before the start does not count.
 */
fun outcomeOf(stop: Stop, record: StopRecord?, state: UnderwayState): StopOutcome {
    val arrived = record?.arrivedAtMillis ?: return StopOutcome.Skipped
    if (stop.kind == StopKind.Meal) return StopOutcome.Visited
    val periodEnd = stop.plannedDeparture.toInstant().toEpochMilli()
    val periodStart = stop.start.toInstant().toEpochMilli()
    val expected = periodEnd - periodStart
    if (expected <= 0) return StopOutcome.TooBrief
    val stayEnd = stayEndMillis(record, state) ?: arrived
    val overlap = maxOf(0L, minOf(stayEnd, periodEnd) - maxOf(arrived, periodStart))
    return if (overlap * 100 >= expected * MIN_STAY_PERCENT) StopOutcome.Visited else StopOutcome.TooBrief
}
