package com.example.hackapp.underway

import com.example.hackapp.data.model.LatLng
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.ItineraryRepository
import com.example.hackapp.itinerary.StartBlock
import com.example.hackapp.itinerary.StartResult
import com.example.hackapp.itinerary.startBlock
import com.example.hackapp.itinerary.startItinerary
import com.example.hackapp.passport.PassportUnavailableException
import com.example.hackapp.passport.StampIdentityResolver
import com.example.hackapp.passport.StampRepository
import com.example.hackapp.passport.StopStampResult
import com.example.hackapp.services.LegEstimator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

sealed interface UnderwayStartResult {
    /** [askExactAlarm] is true when exact alarms are off: offer the permission once, never block. */
    data class Started(val view: UnderwayView, val askExactAlarm: Boolean) : UnderwayStartResult
    data class Blocked(val reason: StartBlock) : UnderwayStartResult
    /** Another Itinerary is Underway. Warn, then call again with `confirmEndCurrent = true`. */
    data class NeedsConfirmation(val current: Itinerary) : UnderwayStartResult
    data object NotFound : UnderwayStartResult
}

data class DispatchResult(val view: UnderwayView?, val rejection: Rejection?)

/**
 * Runs [UnderwayEngine] around storage, alerts and Stamps. Pages and receivers call only this.
 * [resolverFor] must resolve Places including non-ACTIVE ones.
 */
class UnderwayCoordinator(
    private val itineraries: ItineraryRepository,
    private val states: UnderwayRepository,
    private val stamps: StampRepository,
    private val resolverFor: suspend (Itinerary) -> StampIdentityResolver,
    private val estimator: LegEstimator,
    private val clock: UnderwayClock,
    private val scheduler: UnderwayAlertScheduler,
    private val notifier: UnderwayNotifier,
) {
    private val lock = Mutex()

    private fun ctx(itinerary: Itinerary) = UnderwayContext(itinerary, estimator, clock.zone)

    suspend fun start(
        uid: String,
        itineraryId: String,
        origin: LatLng?,
        demoMode: Boolean = false,
        confirmEndCurrent: Boolean = false,
    ): UnderwayStartResult = lock.withLock {
        val now = clock.now()
        val draft = itineraries.getItinerary(uid, itineraryId) ?: return UnderwayStartResult.NotFound
        startBlock(draft, now, clock.zone, demoMode)?.let { return UnderwayStartResult.Blocked(it) }
        val others = underwayItineraries(uid).filter { it.id != itineraryId }
        if (others.isNotEmpty() && !confirmEndCurrent) return UnderwayStartResult.NeedsConfirmation(others.first())
        others.forEach { endCurrent(uid, it, UnderwayEvent.EndForReplacement) }
        val started = when (val r = startItinerary(itineraries, uid, itineraryId, now, clock.zone, demoMode, true)) {
            is StartResult.Started -> r.itinerary
            is StartResult.Blocked -> return UnderwayStartResult.Blocked(r.reason)
            is StartResult.NeedsConfirmation -> return UnderwayStartResult.NeedsConfirmation(r.current)
            StartResult.NotFound -> return UnderwayStartResult.NotFound
        }
        val state = UnderwayEngine.begin(started, now, origin) ?: return UnderwayStartResult.NotFound
        states.save(state)
        reschedule(uid, started, state)
        val view = UnderwayEngine.view(ctx(started), state, now, notificationsAllowed = true)
        UnderwayStartResult.Started(view, askExactAlarm = !scheduler.canScheduleExact())
    }

    suspend fun dispatch(
        uid: String,
        itineraryId: String,
        event: UnderwayEvent,
        notificationsAllowed: Boolean = true,
    ): DispatchResult = lock.withLock {
        val itinerary = itineraries.getItinerary(uid, itineraryId)
        val state = states.get(uid, itineraryId)
        if (itinerary == null || state == null) return DispatchResult(null, Rejection.WrongPhase)
        val t = run(uid, itinerary, state, event)
        DispatchResult(UnderwayEngine.view(ctx(itinerary), t.state, clock.now(), notificationsAllowed), t.rejection)
    }

    /** The view of the Itinerary that is Underway, or null. Call again as time passes. */
    suspend fun view(uid: String, notificationsAllowed: Boolean): UnderwayView? = lock.withLock {
        val itinerary = underwayItineraries(uid).firstOrNull() ?: return null
        val state = states.get(uid, itinerary.id) ?: return null
        UnderwayEngine.view(ctx(itinerary), state, clock.now(), notificationsAllowed)
    }

    /**
     * The view of the Itinerary that is Underway, or null when none is. Emits when the Itinerary
     * or its state changes. It does not tick: re-collect or call [view] as time passes.
     */
    fun observeView(uid: String, notificationsAllowed: () -> Boolean): Flow<UnderwayView?> =
        combine(itineraries.observeItineraries(uid), states.observe(uid)) { all, stateList ->
            val itinerary = all.firstOrNull { it.phase == ItineraryPhase.Underway } ?: return@combine null
            val state = stateList.firstOrNull { it.itineraryId == itinerary.id } ?: return@combine null
            UnderwayEngine.view(ctx(itinerary), state, clock.now(), notificationsAllowed())
        }.distinctUntilChanged()

    /** Results of an ended Itinerary. Awards the Stamps if that is not done yet. Safe to repeat. */
    suspend fun endOfDay(uid: String, itineraryId: String): EndOfDay? = lock.withLock {
        val loaded = itineraries.getItinerary(uid, itineraryId) ?: return null
        val state = states.get(uid, itineraryId)?.takeIf { it.phase == UnderwayPhase.Ended } ?: return null
        val itinerary = UnderwayEngine.syncItinerary(loaded, state, clock.now())
        val award = awardOrNull(itinerary, state)
        if (award != null && !state.stampsAwarded) states.save(state.copy(stampsAwarded = true))
        val results = state.stopOrder.mapNotNull { id ->
            val stop = itinerary.stops.firstOrNull { it.id == id } ?: return@mapNotNull null
            val record = state.records[id]
            val late = record?.arrivedAtMillis?.let {
                val minutes = (it - stop.start.toInstant().toEpochMilli()) / 60_000
                minutes.takeIf { m -> m > 0 }
            }
            StopDayResult(
                stop = stop,
                outcome = state.outcomes[id] ?: StopOutcome.Skipped,
                stamp = award?.resultFor(id) ?: if (award != null) StopStampResult.NoStamp else null,
                arrivedLateMinutes = late,
            )
        }
        EndOfDay(itineraryId, state.endReason ?: EndReason.Finished, results, stampsAvailable = award != null)
    }

    /** App open: create missing state, fire due alerts, reschedule, retry unfinished Stamp awards. */
    suspend fun onAppOpen(uid: String): Unit = lock.withLock { reconcile(uid) }

    /** An alarm fired for this Itinerary. */
    suspend fun onAlarm(uid: String, itineraryId: String): Unit = lock.withLock {
        val itinerary = itineraries.getItinerary(uid, itineraryId) ?: return
        val state = states.get(uid, itineraryId) ?: return
        run(uid, itinerary, state, UnderwayEvent.Tick)

    }

    /** After a reboot or a time change: reschedule the signed-in account's Underway Itinerary. */
    suspend fun onBoot(uid: String?): Unit = lock.withLock {
        if (uid != null) reconcile(uid)
    }

    /** Signing out: this account's alarms and notifications must not outlive the session. */
    suspend fun onSignOut(uid: String): Unit = lock.withLock {
        states.all().filter { it.ownerUid == uid }.forEach {
            scheduler.cancelAll(it.itineraryId)
            notifier.cancelAll(it.itineraryId)
        }
    }

    // ---- internals (lock held) ----

    private suspend fun underwayItineraries(uid: String): List<Itinerary> =
        itineraries.observeItineraries(uid).first().filter { it.phase == ItineraryPhase.Underway }

    private suspend fun reconcile(uid: String) {
        underwayItineraries(uid).forEach { itinerary ->
            if (states.get(uid, itinerary.id) == null) {
                UnderwayEngine.begin(itinerary, clock.now(), null)?.let { states.save(it) }
            }
        }
        states.all().filter { it.ownerUid == uid }.forEach { state ->
            val itinerary = itineraries.getItinerary(uid, state.itineraryId) ?: return@forEach
            val finished = state.phase == UnderwayPhase.Ended && state.stampsAwarded &&
                itinerary.phase == ItineraryPhase.Completed
            if (!finished) run(uid, itinerary, state, UnderwayEvent.Tick)
        }
    }

    private suspend fun endCurrent(uid: String, itinerary: Itinerary, event: UnderwayEvent) {
        val state = states.get(uid, itinerary.id)
            ?: UnderwayEngine.begin(itinerary, clock.now(), null)
            ?: return
        run(uid, itinerary, state, event)
    }

    private suspend fun run(uid: String, itinerary: Itinerary, state: UnderwayState, event: UnderwayEvent): Transition {
        val now = clock.now()
        val t = UnderwayEngine.reduce(ctx(itinerary), state, event, now)
        // State first: it is the source of truth. The Itinerary is re-synced by [reconcile] if we die here.
        if (t.state != state) states.save(t.state)
        val synced = UnderwayEngine.syncItinerary(itinerary, t.state, now)
        // A failed sync must not drop the alerts or the notifications: run them, then rethrow.
        var syncFailure: Exception? = null
        if (synced != itinerary) {
            try {
                itineraries.saveItinerary(synced)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                syncFailure = e
            }
        }
        t.effects.forEach { effect ->
            when (effect) {
                is UnderwayEffect.Notify -> notifier.show(uid, itinerary.id, effect.alert)
                UnderwayEffect.ClearNotifications -> notifier.cancelAll(itinerary.id)
                UnderwayEffect.DayEnded -> Unit
            }
        }
        reschedule(uid, synced, t.state)
        syncFailure?.let { throw it }
        if (t.state.phase == UnderwayPhase.Ended && !t.state.stampsAwarded) {
            if (awardOrNull(synced, t.state) != null) states.save(t.state.copy(stampsAwarded = true))
        }
        return t
    }

    private fun reschedule(uid: String, itinerary: Itinerary, state: UnderwayState) {
        if (state.phase == UnderwayPhase.Ended) {
            scheduler.cancelAll(state.itineraryId)
        } else {
            scheduler.replaceAll(uid, state.itineraryId, UnderwayEngine.alarmsToSchedule(ctx(itinerary), state))
        }
    }

    /** Awards once the day has ended. Null when the Passport is unavailable: retry later. */
    private suspend fun awardOrNull(itinerary: Itinerary, state: UnderwayState) =
        try {
            val toAward = UnderwayEngine.stampItinerary(itinerary, state)
            stamps.award(toAward, resolverFor(toAward), state.endedAtMillis ?: Instant.EPOCH.toEpochMilli())
        } catch (_: PassportUnavailableException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A resolver or storage failure: the award is retried later.
            null
        }
}
