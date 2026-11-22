package com.example.hackapp.underway

import com.example.hackapp.itinerary.Stop

/** The end of the stay: leaving, else the next user action, else the end of the day. */
internal fun stayEndMillis(record: StopRecord, state: UnderwayState): Long? =
    record.leftAtMillis ?: record.nextActionAtMillis ?: state.endedAtMillis

/**
 * Every Stop with a confirmed arrival is visited and earns its Stamp, however short the stay
 * (revision 2026-11-22: the 20% minimum for Events was removed). Without an arrival it is skipped.
 */
@Suppress("UNUSED_PARAMETER")
fun outcomeOf(stop: Stop, record: StopRecord?, state: UnderwayState): StopOutcome =
    if (record?.arrivedAtMillis == null) StopOutcome.Skipped else StopOutcome.Visited
