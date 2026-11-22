package com.example.hackapp.underway

import com.example.hackapp.data.model.LatLng
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.StopStatus
import com.example.hackapp.itinerary.activeStops
import com.example.hackapp.itinerary.completeUnderway
import com.example.hackapp.itinerary.defaultEstimate
import com.example.hackapp.itinerary.defaultLegMode
import com.example.hackapp.itinerary.latLng
import com.example.hackapp.itinerary.legBetween
import com.example.hackapp.itinerary.leaveBy
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.itinerary.transit
import com.example.hackapp.services.LegMode
import java.time.Duration
import java.time.Instant

/**
 * The Underway state machine: `state + event + time -> new state + effects`. Pure: it never reads
 * a clock, saves anything or touches Android. [UnderwayCoordinator] does that around it.
 */
object UnderwayEngine {
    const val ARRIVAL_REMINDER_MINUTES = 30L
    const val LEAVE_ALERT_MINUTES = 30L
    const val AUTO_END_HOURS = 2L

    /** The first Leg opens right away. Null when the Itinerary has no active Stop. */
    fun begin(itinerary: Itinerary, now: Instant, origin: LatLng?, originDistrictId: String? = null): UnderwayState? {
        val order = itinerary.activeStops.sortedBy { it.start.toInstant() }.map { it.id }
        if (order.isEmpty()) return null
        return UnderwayState(
            itineraryId = itinerary.id,
            ownerUid = itinerary.ownerUid,
            stopOrder = order,
            phase = UnderwayPhase.Leg,
            currentStopId = order.first(),
            startedAtMillis = now.toEpochMilli(),
            originLat = origin?.lat,
            originLng = origin?.lng,
            originDistrictId = originDistrictId,
        )
    }

    /** A forgotten Itinerary ends this long after its window closes. */
    fun autoEndAt(ctx: UnderwayContext): Instant =
        ctx.itinerary.windowEnd(ctx.zone).toInstant().plus(Duration.ofHours(AUTO_END_HOURS))

    // ---- queries on the state ----

    private fun UnderwayState.record(id: String) = records[id] ?: StopRecord()

    /** Stops not yet arrived at or skipped, in order. */
    fun pending(state: UnderwayState): List<String> = state.stopOrder.filter {
        val r = state.records[it]
        r?.skippedAtMillis == null && r?.arrivedAtMillis == null
    }

    private fun nextAfter(state: UnderwayState, currentId: String?): String? {
        val index = state.stopOrder.indexOf(currentId)
        val pending = pending(state).toSet()
        return state.stopOrder.drop(index + 1).firstOrNull { it in pending }
    }

    private fun UnderwayContext.stopOf(id: String?): Stop? = id?.let(::stop)

    private fun nextStop(ctx: UnderwayContext, state: UnderwayState): Stop? =
        ctx.stopOf(nextAfter(state, state.currentStopId))

    private fun lateSince(ctx: UnderwayContext, from: Stop, next: Stop): Instant =
        next.start.minusMinutes(ctx.estimator.legBetween(from, next).totalMinutes.toLong()).toInstant()

    // ---- transitions ----

    fun reduce(ctx: UnderwayContext, state: UnderwayState, event: UnderwayEvent, now: Instant): Transition {
        var s = state
        val effects = mutableListOf<UnderwayEffect>()
        if (s.phase != UnderwayPhase.Ended && !now.isBefore(autoEndAt(ctx))) {
            s = endDay(ctx, s, autoEndAt(ctx), EndReason.Automatic)
            effects += UnderwayEffect.ClearNotifications
            effects += UnderwayEffect.DayEnded
        }
        if (s.phase == UnderwayPhase.Ended) {
            val rejection = if (event == UnderwayEvent.Tick) null else Rejection.Ended
            return Transition(s, effects, rejection)
        }
        if (event == UnderwayEvent.Tick) return Transition(fireDue(ctx, s, now, effects), effects)
        if (event == UnderwayEvent.NotificationBannerHandled) {
            return Transition(s.copy(bannerHandled = true), effects)
        }

        val nowMs = now.toEpochMilli()
        val current = s.currentStopId
        val next = nextAfter(s, current)

        val base = s

        // The first user action after arriving ends the stay when nobody taps leaving.
        if (s.phase == UnderwayPhase.AtStop && current != null) {
            val r = s.record(current)
            if (r.nextActionAtMillis == null) {
                s = s.copy(records = s.records + (current to r.copy(nextActionAtMillis = nowMs)))
            }
        }
        fun done(newState: UnderwayState, more: List<UnderwayEffect> = emptyList()) =
            Transition(newState, effects + UnderwayEffect.ClearNotifications + more)
        // A rejected event changes nothing, so it also drops the stay marker.
        fun rejectOriginal(why: Rejection) = Transition(base, effects, why)

        return when (event) {
            is UnderwayEvent.OpenTransport -> when (s.phase) {
                UnderwayPhase.Leg, UnderwayPhase.Arrival ->
                    if (event.stopId != null && event.stopId != current) rejectOriginal(Rejection.StaleStop)
                    else done(s.copy(phase = UnderwayPhase.Arrival, transport = event.choice, handoffAtMillis = nowMs, legTransportChosen = true))
                else -> rejectOriginal(Rejection.WrongPhase)
            }
            UnderwayEvent.ReturnToLeg ->
                if (s.phase == UnderwayPhase.Arrival) done(s.copy(phase = UnderwayPhase.Leg, handoffAtMillis = null))
                else rejectOriginal(Rejection.WrongPhase)
            is UnderwayEvent.ConfirmArrival -> when {
                s.phase != UnderwayPhase.Leg && s.phase != UnderwayPhase.Arrival -> rejectOriginal(Rejection.WrongPhase)
                event.stopId != null && event.stopId != current -> rejectOriginal(Rejection.StaleStop)
                else -> done(
                    s.copy(
                        phase = UnderwayPhase.AtStop,
                        handoffAtMillis = null,
                        legTransportChosen = false,
                        records = s.records + (current!! to s.record(current).copy(arrivedAtMillis = nowMs)),
                    ),
                )
            }
            is UnderwayEvent.Leaving -> when {
                s.phase != UnderwayPhase.AtStop || next == null -> rejectOriginal(Rejection.WrongPhase)
                event.stopId != null && event.stopId != current -> rejectOriginal(Rejection.StaleStop)
                else -> done(
                    s.copy(
                        phase = UnderwayPhase.Leg,
                        currentStopId = next,
                        handoffAtMillis = null,
                        legTransportChosen = false,
                        records = s.records + (current!! to s.record(current).copy(leftAtMillis = nowMs)),
                    ),
                )
            }
            is UnderwayEvent.SkipStop -> when (s.phase) {
                UnderwayPhase.Leg, UnderwayPhase.Arrival -> when {
                    event.stopId != null && event.stopId != current -> rejectOriginal(Rejection.StaleStop)
                    // A notification must never end the day without the confirmation dialog.
                    next == null && event.fromNotification -> rejectOriginal(Rejection.NeedsConfirmation)
                    else -> {
                        val skipped = s.copy(
                            records = s.records + (current!! to s.record(current).copy(skippedAtMillis = nowMs)),
                        )
                        if (next == null) {
                            val ended = endDay(ctx, skipped, now, EndReason.Finished)
                            done(ended, listOf(UnderwayEffect.DayEnded))
                        } else {
                            done(skipped.copy(phase = UnderwayPhase.Leg, currentStopId = next, handoffAtMillis = null, legTransportChosen = false))
                        }
                    }
                }
                UnderwayPhase.AtStop -> when {
                    next == null -> rejectOriginal(Rejection.WrongPhase)
                    event.stopId != null && event.stopId != next -> rejectOriginal(Rejection.StaleStop)
                    else -> done(s.copy(records = s.records + (next to s.record(next).copy(skippedAtMillis = nowMs))))
                }
                UnderwayPhase.Ended -> rejectOriginal(Rejection.Ended)
            }
            is UnderwayEvent.GoAnyway -> when {
                s.phase != UnderwayPhase.AtStop || next == null -> rejectOriginal(Rejection.WrongPhase)
                event.stopId != next -> rejectOriginal(Rejection.StaleStop)
                else -> done(s.copy(dismissedLateWarnings = s.dismissedLateWarnings + next))
            }
            is UnderwayEvent.EndItinerary -> {
                val hasPending = pending(s).isNotEmpty()
                if (hasPending && !event.confirmed) {
                    rejectOriginal(Rejection.NeedsConfirmation)
                } else {
                    val reason = if (hasPending) EndReason.EndedEarly else EndReason.Finished
                    done(endDay(ctx, s, now, reason), listOf(UnderwayEffect.DayEnded))
                }
            }
            UnderwayEvent.EndForReplacement ->
                done(endDay(ctx, s, now, EndReason.Replaced), listOf(UnderwayEffect.DayEnded))
            UnderwayEvent.Tick, UnderwayEvent.NotificationBannerHandled -> Transition(s, effects)
        }
    }

    private fun fireDue(
        ctx: UnderwayContext,
        state: UnderwayState,
        now: Instant,
        effects: MutableList<UnderwayEffect>,
    ): UnderwayState {
        val due = scheduledAlerts(ctx, state).filter { !it.at.isAfter(now) }
        due.forEach { effects += UnderwayEffect.Notify(it) }
        return if (due.isEmpty()) state else state.copy(firedAlerts = state.firedAlerts + due.map { it.id })
    }

    /** Ends the day at [at]: remaining Stops are skipped and each Stop's outcome is fixed. */
    private fun endDay(ctx: UnderwayContext, state: UnderwayState, at: Instant, reason: EndReason): UnderwayState {
        val atMs = at.toEpochMilli()
        val records = state.records.toMutableMap()
        val current = state.currentStopId
        if (state.phase == UnderwayPhase.AtStop && current != null) {
            val r = records[current] ?: StopRecord()
            records[current] = if (reason == EndReason.Automatic) {
                r.copy(nextActionAtMillis = r.nextActionAtMillis ?: atMs)
            } else {
                r.copy(leftAtMillis = r.leftAtMillis ?: atMs)
            }
        }
        pending(state).forEach { records[it] = (records[it] ?: StopRecord()).copy(skippedAtMillis = atMs) }
        val ended = state.copy(
            phase = UnderwayPhase.Ended,
            records = records,
            endReason = reason,
            endedAtMillis = atMs,
        )
        return ended.copy(outcomes = computeOutcomes(ctx, ended))
    }

    private fun computeOutcomes(ctx: UnderwayContext, state: UnderwayState): Map<String, StopOutcome> =
        state.stopOrder.mapNotNull { id -> ctx.stop(id)?.let { id to outcomeOf(it, state.records[id], state) } }.toMap()

    // ---- alerts ----

    /** Every alert that applies to the current situation, fired or not. */
    fun desiredAlerts(ctx: UnderwayContext, state: UnderwayState): List<PlannedAlert> {
        val current = ctx.stopOf(state.currentStopId) ?: return emptyList()
        return when (state.phase) {
            UnderwayPhase.Leg, UnderwayPhase.Arrival -> listOf(
                PlannedAlert(
                    id = "${AlertKind.ArrivalReminder.name}:${current.id}",
                    kind = AlertKind.ArrivalReminder,
                    at = current.start.plusMinutes(ARRIVAL_REMINDER_MINUTES).toInstant(),
                    highPriority = false,
                    stopId = current.id,
                    stopName = current.name,
                    stopStart = current.start,
                    // Skipping the last remaining Stop would end the day: no Skip, the app asks first.
                    skipStopId = current.id.takeIf { nextStop(ctx, state) != null },
                    isMeal = current.kind == StopKind.Meal,
                ),
            )
            UnderwayPhase.AtStop -> {
                val next = nextStop(ctx, state) ?: return emptyList()
                val leave = leaveBy(current, next, ctx.estimator)
                val alerts = mutableListOf(
                    PlannedAlert(
                        id = "${AlertKind.LeaveBy.name}:${current.id}:${next.id}",
                        kind = AlertKind.LeaveBy,
                        at = leave.minusMinutes(LEAVE_ALERT_MINUTES).toInstant(),
                        highPriority = true,
                        stopId = current.id,
                        stopName = current.name,
                        stopStart = current.start,
                        leaveBy = leave,
                        nextStopName = next.name,
                        skipStopId = next.id,
                        isMeal = next.kind == StopKind.Meal,
                    ),
                )
                if (next.id !in state.dismissedLateWarnings) {
                    alerts += PlannedAlert(
                        id = "${AlertKind.LateWarning.name}:${next.id}",
                        kind = AlertKind.LateWarning,
                        at = lateSince(ctx, current, next).plusSeconds(1),
                        highPriority = false,
                        stopId = next.id,
                        stopName = next.name,
                        stopStart = next.start,
                        skipStopId = next.id,
                        isMeal = next.kind == StopKind.Meal,
                    )
                }
                alerts
            }
            UnderwayPhase.Ended -> emptyList()
        }
    }

    /** Alerts still to be delivered: applicable and not fired yet. */
    fun scheduledAlerts(ctx: UnderwayContext, state: UnderwayState): List<PlannedAlert> =
        desiredAlerts(ctx, state).filter { it.id !in state.firedAlerts }.sortedBy { it.at }

    /**
     * Everything to hand to the alarm scheduler: the alerts still to deliver, plus a wake-up at the
     * automatic end so a forgotten Itinerary ends and awards its Stamps with the app closed.
     * The AutoEnd entry is never shown as a notification.
     */
    fun alarmsToSchedule(ctx: UnderwayContext, state: UnderwayState): List<PlannedAlert> {
        if (state.phase == UnderwayPhase.Ended) return emptyList()
        val end = autoEndAt(ctx)
        val autoEnd = PlannedAlert(
            id = "${AlertKind.AutoEnd.name}:${state.itineraryId}",
            kind = AlertKind.AutoEnd,
            at = end,
            highPriority = false,
            stopId = "",
            stopName = "",
            stopStart = end.atZone(ctx.zone).toOffsetDateTime(),
        )
        return scheduledAlerts(ctx, state) + autoEnd
    }

    /**
     * The next moment after [time] when something happens: an alert, a Leave By, an expected
     * arrival, a Stop start or the automatic end. A demo clock jumps here. Null once the day has ended.
     */
    fun nextMomentAfter(ctx: UnderwayContext, state: UnderwayState, time: Instant): Instant? {
        if (state.phase == UnderwayPhase.Ended) return null
        val moments = mutableListOf(autoEndAt(ctx))
        scheduledAlerts(ctx, state).forEach { moments += it.at }
        pending(state).forEach { id -> ctx.stop(id)?.let { moments += it.start.toInstant() } }
        val shown = view(ctx, state, time, notificationsAllowed = true)
        val leave = shown.leaveBy?.toInstant()
        leave?.let { moments += it }
        // On the Leg the arrival follows the clock once Leave By has passed: it is not a fixed moment.
        val arrivalIsFixed = state.phase == UnderwayPhase.Arrival || (leave != null && !time.isAfter(leave))
        if (arrivalIsFixed) shown.expectedArrival?.let { moments += it.toInstant() }
        return moments.filter { it.isAfter(time) }.minOrNull()
    }

    // ---- views and results ----

    /**
     * How the Leg to [to] is travelled: what the person picked for this Leg, else walking when the
     * default says so, else the last choice between transit and Uber (transit at first).
     */
    private fun legMode(
        ctx: UnderwayContext,
        state: UnderwayState,
        fromDistrict: String?,
        from: LatLng,
        to: Stop,
    ): LegMode {
        if (state.legTransportChosen) {
            return when (state.transport) {
                TransportChoice.Walk -> LegMode.WALK
                TransportChoice.Uber -> LegMode.RIDE_HAIL
                TransportChoice.Transit -> LegMode.TRANSIT
            }
        }
        return when {
            defaultLegMode(fromDistrict, to.districtId, from, to.latLng, ctx.estimator) == LegMode.WALK -> LegMode.WALK
            state.transport == TransportChoice.Uber -> LegMode.RIDE_HAIL
            else -> LegMode.TRANSIT
        }
    }

    fun view(ctx: UnderwayContext, state: UnderwayState, now: Instant, notificationsAllowed: Boolean): UnderwayView {
        val current = ctx.stopOf(state.currentStopId).takeIf { state.phase != UnderwayPhase.Ended }
        val next = if (current != null) nextStop(ctx, state) else null
        val legFrom = if (current != null && state.phase != UnderwayPhase.AtStop) {
            state.stopOrder.takeWhile { it != current.id }
                .lastOrNull { state.records[it]?.arrivedAtMillis != null }?.let(ctx::stop)
        } else {
            null
        }
        val originPoint = if (state.originLat != null && state.originLng != null) {
            LatLng(state.originLat, state.originLng)
        } else {
            null
        }
        val leave = when {
            current == null -> null
            state.phase == UnderwayPhase.AtStop -> leaveBy(current, next, ctx.estimator)
            legFrom != null -> leaveBy(legFrom, current, ctx.estimator)
            originPoint != null -> current.start.minusMinutes(
                ctx.estimator.defaultEstimate(state.originDistrictId, current.districtId, originPoint, current.latLng)
                    .totalMinutes.toLong(),
            )
            else -> null
        }
        // As the Leg hero shows it: leave at Leave By (or now if later) plus the travel time of the
        // chosen transport. With no previous Stop there is no estimate, so it stays null.
        val expected = if (current != null && legFrom != null && leave != null) {
            val mode = legMode(ctx, state, legFrom.districtId, legFrom.latLng, current)
            val minutes = ctx.estimator.estimate(legFrom.latLng, current.latLng, mode).travelMinutes
            // On Arrival the clock stops at the handoff, so the time does not move every minute.
            val from = if (state.phase == UnderwayPhase.Arrival) {
                state.handoffAtMillis?.let(Instant::ofEpochMilli) ?: now
            } else {
                now
            }
            val departs = maxOf(from, leave.toInstant())
            departs.plusSeconds(minutes * 60L).atZone(ctx.zone).toOffsetDateTime()
        } else {
            null
        }
        val walking = current != null && state.phase != UnderwayPhase.AtStop && when {
            legFrom != null -> legMode(ctx, state, legFrom.districtId, legFrom.latLng, current) == LegMode.WALK
            originPoint != null -> legMode(ctx, state, state.originDistrictId, originPoint, current) == LegMode.WALK
            else -> false
        }
        val late = if (state.phase == UnderwayPhase.AtStop && current != null && next != null &&
            next.id !in state.dismissedLateWarnings && now.isAfter(lateSince(ctx, current, next))
        ) {
            next
        } else {
            null
        }
        return UnderwayView(
            itineraryId = state.itineraryId,
            phase = state.phase,
            currentStop = current,
            legFrom = legFrom,
            expectedArrival = expected,
            leaveBy = leave,
            nextStop = next,
            isLastStop = current != null && next == null,
            transport = if (state.transport == TransportChoice.Uber) TransportChoice.Uber else TransportChoice.Transit,
            walking = walking,
            remainingStops = pending(state).size,
            lateWarningStop = late,
            endNeedsConfirmation = pending(state).isNotEmpty(),
            showNotificationBanner = !notificationsAllowed && !state.bannerHandled && state.phase != UnderwayPhase.Ended,
            endedReason = state.endReason,
        )
    }

    /**
     * Syncs the Itinerary with the state: arrived Stops are Confirmed, skipped ones Skipped, and
     * an ended day completes the Itinerary. A too-brief Event stays Confirmed (it was visited).
     */
    fun syncItinerary(itinerary: Itinerary, state: UnderwayState, now: Instant): Itinerary {
        val stops = itinerary.stops.map { stop ->
            val r = state.records[stop.id]
            when {
                r?.arrivedAtMillis != null -> stop.copy(status = StopStatus.Confirmed)
                r?.skippedAtMillis != null -> stop.copy(status = StopStatus.Skipped)
                else -> stop
            }
        }
        val synced = itinerary.copy(stops = stops)
        return if (state.phase == UnderwayPhase.Ended) {
            completeUnderway(synced, Instant.ofEpochMilli(state.endedAtMillis ?: now.toEpochMilli()))
        } else {
            synced.copy(phase = ItineraryPhase.Underway)
        }
    }

    /** The Completed Itinerary to award: too-brief Events do not count, so they are Skipped. */
    fun stampItinerary(itinerary: Itinerary, state: UnderwayState): Itinerary {
        val synced = syncItinerary(itinerary, state, Instant.ofEpochMilli(state.endedAtMillis ?: 0L))
        return synced.copy(
            stops = synced.stops.map {
                if (state.outcomes[it.id] == StopOutcome.TooBrief) it.copy(status = StopStatus.Skipped) else it
            },
        )
    }
}
