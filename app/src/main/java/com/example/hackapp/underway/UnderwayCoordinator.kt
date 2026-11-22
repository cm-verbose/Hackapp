package com.example.hackapp.underway

import com.example.hackapp.data.model.LatLng
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.ItineraryRepository
import com.example.hackapp.itinerary.StartBlock
import com.example.hackapp.itinerary.StartResult
import com.example.hackapp.itinerary.archiveExpiredDrafts
import com.example.hackapp.itinerary.reopenArchivedDrafts
import com.example.hackapp.itinerary.startBlock
import com.example.hackapp.itinerary.startItinerary
import com.example.hackapp.passport.PassportUnavailableException
import com.example.hackapp.passport.StampIdentityResolver
import com.example.hackapp.passport.StampRepository
import com.example.hackapp.passport.StopStampResult
import com.example.hackapp.services.LegEstimator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    /** The one source of time for Underway and for Review's Start check. */
    val clock: UnderwayClock,
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
        originDistrictId: String? = null,
    ): UnderwayStartResult = lock.withLock {
        // Demo mode belongs to the account that turned it on.
        val demoClock = (clock as? DemoClock)?.takeIf { demoMode && it.isSimulated && it.ownerUid == uid }
        val demoAllowed = demoMode && (clock !is DemoClock || demoClock != null)
        val current = if (demoAllowed) clock.now() else clock.realNow()
        val draft = itineraries.getItinerary(uid, itineraryId) ?: return UnderwayStartResult.NotFound
        startBlock(draft, current, clock.zone, demoAllowed)?.let { return UnderwayStartResult.Blocked(it) }
        val others = underwayItineraries(uid).filter { it.id != itineraryId }
        if (others.isNotEmpty() && !confirmEndCurrent) return UnderwayStartResult.NeedsConfirmation(others.first())
        others.forEach { endCurrent(uid, it, UnderwayEvent.EndForReplacement) }
        // Only now, after any confirmation: the old Itinerary has ended at its own time.
        val now = demoClock?.let { demo ->
            val at = demoStartInstant(draft, origin, originDistrictId, estimator, clock.zone) ?: current
            demo.moveTo(at, itineraryId)
            at
        } ?: current
        val started = when (val r = startItinerary(itineraries, uid, itineraryId, clock.realNow(), clock.zone, demoAllowed, true)) {
            is StartResult.Started -> r.itinerary
            is StartResult.Blocked -> return UnderwayStartResult.Blocked(r.reason)
            is StartResult.NeedsConfirmation -> return UnderwayStartResult.NeedsConfirmation(r.current)
            StartResult.NotFound -> return UnderwayStartResult.NotFound
        }
        val state = UnderwayEngine.begin(started, now, origin, originDistrictId)?.copy(demoRun = demoClock != null)
            ?: return UnderwayStartResult.NotFound
        states.save(state)
        reschedule(uid, started, state)
        val view = UnderwayEngine.view(ctx(started), state, now, notificationsAllowed = true)
        UnderwayStartResult.Started(view, askExactAlarm = !state.demoRun && !scheduler.canScheduleExact())
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
        DispatchResult(UnderwayEngine.view(ctx(itinerary), t.state, nowFor(t.state), notificationsAllowed), t.rejection)
    }

    /** The view of the Itinerary that is Underway, or null. Call again as time passes. */
    suspend fun view(uid: String, notificationsAllowed: Boolean): UnderwayView? = lock.withLock {
        val itinerary = underwayItineraries(uid).firstOrNull() ?: return null
        val state = states.get(uid, itinerary.id) ?: return null
        UnderwayEngine.view(ctx(itinerary), state, nowFor(state), notificationsAllowed)
    }

    /**
     * The time an Underway page draws: real for a real Itinerary, simulated for a demo run.
     * Read-only, and never throws: a failed read falls back to the shared clock.
     */
    suspend fun displayNow(uid: String): Instant = try {
        val state = underwayItineraries(uid).firstOrNull()?.let { states.get(uid, it.id) }
        state?.let(::nowFor) ?: clock.now()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        clock.now()
    }

    /**
     * The view of the Itinerary that is Underway, or null when none is. Emits when the Itinerary
     * or its state changes. It does not tick: re-collect or call [view] as time passes.
     */
    fun observeView(uid: String, notificationsAllowed: () -> Boolean): Flow<UnderwayView?> =
        combine(itineraries.observeItineraries(uid), states.observe(uid)) { all, stateList ->
            val itinerary = all.firstOrNull { it.phase == ItineraryPhase.Underway } ?: return@combine null
            val state = stateList.firstOrNull { it.itineraryId == itinerary.id } ?: return@combine null
            UnderwayEngine.view(ctx(itinerary), state, nowFor(state), notificationsAllowed())
        }.distinctUntilChanged()

    /** Results of an ended Itinerary. Awards the Stamps if that is not done yet. Safe to repeat. */
    suspend fun endOfDay(uid: String, itineraryId: String): EndOfDay? = lock.withLock {
        val loaded = itineraries.getItinerary(uid, itineraryId) ?: return null
        val state = states.get(uid, itineraryId)?.takeIf { it.phase == UnderwayPhase.Ended } ?: return null
        val itinerary = UnderwayEngine.syncItinerary(loaded, state, nowFor(state))
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
        withContext(NonCancellable) {
            states.all().filter { it.ownerUid == uid }.forEach {
                scheduler.cancelAll(it.itineraryId)
                notifier.cancelAll(it.itineraryId)
            }
            // Demo mode never outlives the session. Its run ends here, keeping its Stamps.
            if ((clock as? DemoClock)?.isSimulated == true) exitDemo(uid, force = true)
        }
    }

    // ---- demo mode (see docs/contracts/demo.md) ----

    /** Opens demo mode on its pinned date and reopens archived drafts. Refused while a real Itinerary is Underway. */
    suspend fun enableDemo(uid: String): DemoEnableResult = lock.withLock {
        val demo = demoClock()
        underwayItineraries(uid).firstOrNull { !isDemoRun(uid, it) }
            ?.let { return DemoEnableResult.RealItineraryUnderway(it) }
        if (demo.isSimulated) return DemoEnableResult.Enabled
        reopenArchivedDrafts(itineraries, uid)
        demo.open(demo.pinnedStart(), uid)
        DemoEnableResult.Enabled
    }

    /** Ends every unfinished demo run at its simulated time, then returns to real time. */
    suspend fun disableDemo(uid: String): Unit = lock.withLock {
        withContext(NonCancellable) { exitDemo(uid, force = false) }
    }

    /**
     * Ends unfinished demo runs, including one left half done by an earlier failed exit, then
     * returns to real time. With [force] the clock stops even if a run could not be saved: that
     * run then ends on its owner's next app open.
     */
    private suspend fun exitDemo(uid: String, force: Boolean) {
        val demo = demoClock()
        var failure: Exception? = null
        states.all().filter { it.demoRun }.forEach { state ->
            try {
                val itinerary = itineraries.getItinerary(state.ownerUid, state.itineraryId) ?: return@forEach
                if (state.phase == UnderwayPhase.Ended && itinerary.phase != ItineraryPhase.Underway) return@forEach
                run(state.ownerUid, itinerary, state, UnderwayEvent.EndItinerary(confirmed = true))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = failure ?: e
            }
        }
        if (!force) failure?.let { throw it }
        // Reopened drafts whose date has passed go back. This also runs on the next launch.
        try {
            archiveExpiredDrafts(itineraries, uid, clock.realNow(), clock.zone)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Unit
        }
        demo.stop()
    }

    /** Jumps to the next moment of the demo run and fires what is due. */
    suspend fun advanceDemo(uid: String): DemoAdvance = lock.withLock {
        val demo = (clock as? DemoClock)?.takeIf { it.isSimulated } ?: return DemoAdvance.NotEnabled
        val itinerary = underwayItineraries(uid).firstOrNull() ?: return DemoAdvance.NothingUnderway
        val state = states.get(uid, itinerary.id)?.takeIf { it.demoRun && it.phase != UnderwayPhase.Ended }
            ?: return DemoAdvance.NothingUnderway
        // Null only past the automatic end: the Tick below then ends the day.
        UnderwayEngine.nextMomentAfter(ctx(itinerary), state, clock.now())?.let { demo.advanceTo(it) }
        val t = run(uid, itinerary, state, UnderwayEvent.Tick)
        DemoAdvance.Moved(clock.now(), t.state.phase == UnderwayPhase.Ended)
    }

    /** True while this account has an unfinished demo run. */
    fun observeDemoRun(uid: String): Flow<Boolean> =
        combine(itineraries.observeItineraries(uid), states.observe(uid)) { all, stateList ->
            val itinerary = all.firstOrNull { it.phase == ItineraryPhase.Underway }
            stateList.any { it.itineraryId == itinerary?.id && it.demoRun && it.phase != UnderwayPhase.Ended }
        }.distinctUntilChanged()

    private fun demoClock(): DemoClock = checkNotNull(clock as? DemoClock) { "Demo mode needs a DemoClock" }

    // ---- internals (lock held) ----

    /** A demo run reads simulated time. A real Itinerary always reads real time. */
    private fun nowFor(state: UnderwayState): Instant = if (state.demoRun) clock.now() else clock.realNow()

    /** True for the Itinerary demo Start recorded. A real run with missing state is never one. */
    private fun wasStartedInDemo(itinerary: Itinerary): Boolean =
        (clock as? DemoClock)?.let {
            it.isSimulated && it.ownerUid == itinerary.ownerUid && it.runItineraryId == itinerary.id
        } == true

    private suspend fun isDemoRun(uid: String, itinerary: Itinerary): Boolean =
        states.get(uid, itinerary.id)?.demoRun ?: wasStartedInDemo(itinerary)

    /** The state of an Itinerary that lost it. */
    private fun beginMissing(itinerary: Itinerary): UnderwayState? {
        val demo = wasStartedInDemo(itinerary)
        return UnderwayEngine.begin(itinerary, if (demo) clock.now() else clock.realNow(), null)?.copy(demoRun = demo)
    }

    /** The latest time this run recorded. Simulated, so it can be later than real time. */
    private fun latestRecorded(state: UnderwayState): Instant {
        val times = state.records.values.flatMap {
            listOfNotNull(it.arrivedAtMillis, it.leftAtMillis, it.skippedAtMillis, it.nextActionAtMillis)
        } + listOfNotNull(state.startedAtMillis, state.handoffAtMillis)
        return Instant.ofEpochMilli(times.max())
    }

    private suspend fun underwayItineraries(uid: String): List<Itinerary> =
        itineraries.observeItineraries(uid).first().filter { it.phase == ItineraryPhase.Underway }

    private suspend fun reconcile(uid: String) {
        // Sign-out may not have finished (process death, expired session): never cross accounts.
        (clock as? DemoClock)?.takeIf { it.isSimulated && it.ownerUid != uid }?.let {
            exitDemo(it.ownerUid ?: uid, force = true)
        }
        underwayItineraries(uid).forEach { itinerary ->
            if (states.get(uid, itinerary.id) == null) {
                beginMissing(itinerary)?.let { states.save(it) }
            }
        }
        states.all().filter { it.ownerUid == uid }.forEach { state ->
            val itinerary = itineraries.getItinerary(uid, state.itineraryId) ?: return@forEach
            val finished = state.phase == UnderwayPhase.Ended && state.stampsAwarded &&
                itinerary.phase == ItineraryPhase.Completed
            if (finished) return@forEach
            // Demo settings were lost: a demo run must not stay Underway with no alarms.
            val orphan = state.demoRun && !clock.isSimulated && state.phase != UnderwayPhase.Ended
            if (orphan) {
                val at = maxOf(clock.realNow(), latestRecorded(state))
                run(uid, itinerary, state, UnderwayEvent.EndItinerary(confirmed = true), at)
            } else {
                run(uid, itinerary, state, UnderwayEvent.Tick)
            }
        }
    }

    private suspend fun endCurrent(uid: String, itinerary: Itinerary, event: UnderwayEvent) {
        val state = states.get(uid, itinerary.id)
            ?: beginMissing(itinerary)
            ?: return
        run(uid, itinerary, state, event)
    }

    private suspend fun run(
        uid: String,
        itinerary: Itinerary,
        state: UnderwayState,
        event: UnderwayEvent,
        now: Instant = nowFor(state),
    ): Transition {
        // Saved at or after every recorded time, so a killed process never restarts before them.
        if (state.demoRun) (clock as? DemoClock)?.checkpoint()
        val t = UnderwayEngine.reduce(ctx(itinerary), state, event, now)
        // State first: it is the source of truth. The Itinerary is re-synced by [reconcile] if we die here.
        if (t.state != state) states.save(t.state)
        // A demo run keeps real time here, so lists sorted by last change stay truthful.
        val computed = UnderwayEngine.syncItinerary(itinerary, t.state, now)
        val synced = if (state.demoRun) computed.copy(updatedAtMillis = itinerary.updatedAtMillis) else computed
        // A failed sync must not drop the alerts or the notifications: run them, then rethrow.
        var syncFailure: Exception? = null
        if (synced != itinerary) {
            try {
                itineraries.saveItinerary(if (state.demoRun) synced.copy(updatedAtMillis = clock.realNow().toEpochMilli()) else synced)
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
        // A demo run never holds a real alarm: its alerts fire on Next moment.
        if (state.phase == UnderwayPhase.Ended || state.demoRun) {
            scheduler.cancelAll(state.itineraryId)
        } else {
            scheduler.replaceAll(uid, state.itineraryId, UnderwayEngine.alarmsToSchedule(ctx(itinerary), state))
        }
    }

    /** Awards once the day has ended. Null when the Passport is unavailable: retry later. */
    private suspend fun awardOrNull(itinerary: Itinerary, state: UnderwayState) =
        try {
            val toAward = UnderwayEngine.stampItinerary(itinerary, state)
            // A simulated date must not sort above Stamps earned later in real time.
            val awardedAt = if (state.demoRun) clock.realNow().toEpochMilli()
            else state.endedAtMillis ?: Instant.EPOCH.toEpochMilli()
            stamps.award(toAward, resolverFor(toAward), awardedAt)
        } catch (_: PassportUnavailableException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A resolver or storage failure: the award is retried later.
            null
        }
}
