package com.example.hackapp.scaffold.screens.planning

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hackapp.auth.AuthService
import com.example.hackapp.data.Event
import com.example.hackapp.itinerary.BudgetPrices
import com.example.hackapp.itinerary.BudgetSummary
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.ItineraryRepository
import com.example.hackapp.itinerary.PlanningContext
import com.example.hackapp.itinerary.StartBlock
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.StopRemovalPlan
import com.example.hackapp.itinerary.StopStatus
import com.example.hackapp.itinerary.activeStops
import com.example.hackapp.itinerary.budgetSummary
import com.example.hackapp.itinerary.leaveBy
import com.example.hackapp.itinerary.planStopRemoval
import com.example.hackapp.itinerary.rescheduleOptions
import com.example.hackapp.itinerary.rescheduled
import com.example.hackapp.itinerary.startBlock
import com.example.hackapp.services.LegEstimator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** One row of the review list, numbered like its map marker. */
@Immutable
data class ReviewStop(
    val stop: Stop,
    /** 1-based, in visit order. */
    val number: Int,
    val highlighted: Boolean,
    /** The earlier of the Planned Departure and the time needed to reach the next Stop. */
    val leaveBy: OffsetDateTime,
)

/** What the Start button can do. */
sealed interface ReviewStartState {
    /** [demoOverride] is true when only demo mode makes it ready. */
    data class Ready(val demoOverride: Boolean = false) : ReviewStartState
    /** The Itinerary is on a later date. */
    data class NotToday(val date: LocalDate) : ReviewStartState
    /** The date and time window have passed. */
    data class WindowEnded(val date: LocalDate) : ReviewStartState
    data object NoEvents : ReviewStartState
    /** Already Underway, Past or Archived. Hide Start. */
    data object NotPlanning : ReviewStartState
}

sealed interface ReviewDialog {
    /**
     * Another Itinerary is Underway. Confirming moves it to Past. [confirmedStops] keep their
     * Stamps; [skippedStops] (every other Stop not yet skipped) become skipped.
     */
    data class EndCurrent(
        val current: Itinerary,
        val confirmedStops: List<Stop>,
        val skippedStops: List<Stop>,
    ) : ReviewDialog

    /** Removing [removed] leaves [affected] Stops that can no longer be reached by their start. */
    data class RemoveAffects(
        val removed: Stop,
        val affected: List<Stop>,
        val canReschedule: Boolean,
    ) : ReviewDialog
}

/** Another showtime of the same title and venue for an affected Stop. */
@Immutable
data class RescheduleOption(
    val eventId: String,
    val start: OffsetDateTime,
    val plannedDeparture: OffsetDateTime,
    val hasPublishedEnd: Boolean,
)

/** Picking showtimes for affected Stops, one at a time. [options] empty means none is left. */
@Immutable
data class RescheduleStep(
    val stop: Stop,
    val options: List<RescheduleOption>,
    /** 0-based. */
    val index: Int,
    val total: Int,
    /** Every affected Stop that Réorganiser would remove, including ones already rescheduled. */
    val affected: List<Stop> = emptyList(),
)

/** What Start returned. */
sealed interface ReviewStartOutcome {
    data class Started(val itineraryId: String, val askExactAlarm: Boolean) : ReviewStartOutcome
    data class NeedsConfirmation(val current: Itinerary) : ReviewStartOutcome
    data object Blocked : ReviewStartOutcome
    data object NotFound : ReviewStartOutcome
}

/**
 * Starts an Itinerary. The app passes `UnderwayCoordinator.start`, the only code path that moves an
 * Itinerary to Underway, so its state and alerts always exist.
 */
fun interface ReviewStarter {
    suspend fun start(uid: String, itineraryId: String, demoMode: Boolean, confirmEndCurrent: Boolean): ReviewStartOutcome
}

sealed interface ReviewNavigation {
    /** [askExactAlarm]: offer the exact-alarm permission before leaving Review. */
    data class Started(val itineraryId: String, val askExactAlarm: Boolean = false) : ReviewNavigation

    /** Selection for the gap after [afterStopId] (null = before the first Stop), in [districtId]. */
    data class BackToSelection(
        val itineraryId: String,
        val afterStopId: String?,
        val districtId: String,
    ) : ReviewNavigation
}

data class ItineraryReviewUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val itinerary: Itinerary? = null,
    val stops: List<ReviewStop> = emptyList(),
    val highlightedStopId: String? = null,
    val budget: BudgetSummary? = null,
    val start: ReviewStartState = ReviewStartState.NotPlanning,
    val dialog: ReviewDialog? = null,
    val reschedule: RescheduleStep? = null,
    val busy: Boolean = false,
    val actionFailed: Boolean = false,
    val navigation: ReviewNavigation? = null,
)

/** Active Stops in visit order with their numbers and Leave By times. */
fun reviewStops(itinerary: Itinerary, highlightedId: String?, estimator: LegEstimator): List<ReviewStop> =
    itinerary.activeStops.mapIndexed { index, stop ->
        ReviewStop(
            stop = stop,
            number = index + 1,
            highlighted = stop.id == highlightedId,
            leaveBy = leaveBy(itinerary, stop.id, estimator) ?: stop.plannedDeparture,
        )
    }

/** Start works on the Itinerary's date, except in demo mode. */
fun reviewStartState(itinerary: Itinerary, now: Instant, zone: ZoneId, demoMode: Boolean): ReviewStartState {
    val date = itinerary.date
    return when (startBlock(itinerary, now, zone, demoMode)) {
        null -> ReviewStartState.Ready(
            demoOverride = demoMode && startBlock(itinerary, now, zone, demoMode = false) != null,
        )
        StartBlock.NotInPlanning -> ReviewStartState.NotPlanning
        StartBlock.NoEvents -> ReviewStartState.NoEvents
        StartBlock.WrongDate -> ReviewStartState.NotToday(date)
        StartBlock.WindowEnded -> ReviewStartState.WindowEnded(date)
    }
}

fun endCurrentWarning(current: Itinerary): ReviewDialog.EndCurrent = ReviewDialog.EndCurrent(
    current = current,
    confirmedStops = current.stops.filter { it.status == StopStatus.Confirmed },
    skippedStops = current.stops.filter { it.status == StopStatus.Planned },
)

/**
 * The review rules without Android, so they run in plain unit tests. The ViewModel only launches
 * these on its scope.
 */
class ItineraryReviewHolder(
    private val itineraryId: String,
    private val uid: () -> String?,
    private val itineraries: ItineraryRepository,
    private val estimator: LegEstimator,
    private val zone: ZoneId,
    private val loadContext: suspend () -> PlanningContext,
    private val loadPrices: suspend (Itinerary) -> BudgetPrices,
    private val loadEvents: suspend (Itinerary) -> List<Event>,
    private val starter: ReviewStarter,
    private val demoMode: Boolean = false,
    private val clock: () -> Instant = Instant::now,
) {
    private val _state = MutableStateFlow(ItineraryReviewUiState())
    val state: StateFlow<ItineraryReviewUiState> = _state.asStateFlow()

    private var context: PlanningContext? = null
    private var plan: StopRemovalPlan? = null
    private var events: List<Event> = emptyList()
    private var base: Itinerary? = null
    private var queue: List<Stop> = emptyList()
    private var canReschedule = false

    suspend fun load() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        try {
            val owner = checkNotNull(uid())
            val itinerary = checkNotNull(itineraries.getItinerary(owner, itineraryId))
            context = context ?: loadContext()
            publish(itinerary, pricesRequired = true)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(loading = false, loadFailed = true) }
        }
    }

    /**
     * Shows [itinerary] first, then its budget. When [pricesRequired] is false a price-read failure
     * keeps the earlier budget: the Itinerary is already saved, so it is not an action failure.
     */
    private suspend fun publish(itinerary: Itinerary, pricesRequired: Boolean = false) {
        val ctx = checkNotNull(context)
        _state.update {
            it.copy(
                loading = false,
                loadFailed = false,
                itinerary = itinerary,
                stops = reviewStops(itinerary, it.highlightedStopId, estimator),
                start = reviewStartState(itinerary, clock(), zone, demoMode),
            )
        }
        try {
            val budget = budgetSummary(itinerary, ctx, loadPrices(itinerary))
            _state.update { it.copy(budget = budget) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (pricesRequired) throw e
        }
    }

    /** Recomputes Start for the current time. Call on resume: a page left open past midnight changes. */
    fun refreshStart() = _state.update { state ->
        val itinerary = state.itinerary ?: return@update state
        state.copy(start = reviewStartState(itinerary, clock(), zone, demoMode))
    }

    fun selectStop(id: String?) = _state.update { state ->
        val next = when {
            id == null || id == state.highlightedStopId -> null
            state.stops.none { it.stop.id == id } -> state.highlightedStopId
            else -> id
        }
        state.copy(
            highlightedStopId = next,
            stops = state.stops.map { it.copy(highlighted = it.stop.id == next) },
        )
    }

    suspend fun start() {
        if (_state.value.busy || _state.value.start !is ReviewStartState.Ready) return
        runStart(confirmEndCurrent = false)
    }

    suspend fun confirmDialog() {
        if (_state.value.busy || _state.value.dialog !is ReviewDialog.EndCurrent) return
        _state.update { it.copy(dialog = null) }
        runStart(confirmEndCurrent = true)
    }

    private suspend fun runStart(confirmEndCurrent: Boolean) {
        val owner = uid() ?: return failed()
        _state.update { it.copy(busy = true, actionFailed = false) }
        try {
            when (val result = starter.start(owner, itineraryId, demoMode, confirmEndCurrent)) {
                is ReviewStartOutcome.Started -> _state.update {
                    it.copy(busy = false, navigation = ReviewNavigation.Started(result.itineraryId, result.askExactAlarm))
                }
                is ReviewStartOutcome.NeedsConfirmation -> _state.update {
                    it.copy(busy = false, dialog = endCurrentWarning(result.current))
                }
                ReviewStartOutcome.Blocked -> {
                    itineraries.getItinerary(owner, itineraryId)?.let { publish(it) }
                    _state.update { it.copy(busy = false) }
                }
                ReviewStartOutcome.NotFound -> failed()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed()
        }
    }

    suspend fun remove(stopId: String) {
        val current = _state.value
        val itinerary = current.itinerary ?: return
        if (current.busy || itinerary.phase != ItineraryPhase.Planning) return
        val removal = planStopRemoval(itinerary, stopId, estimator, zone) ?: return
        _state.update { it.copy(busy = true, actionFailed = false) }
        try {
            if (!removal.hasAffected) {
                save(removal)
                return
            }
            val ctx = checkNotNull(context).copy(now = clock())
            events = loadEvents(itinerary)
            plan = removal
            canReschedule = removal.affected.all {
                rescheduleOptions(removal.reorganized, it, events, ctx, estimator).isNotEmpty()
            }
            _state.update {
                it.copy(
                    busy = false,
                    dialog = ReviewDialog.RemoveAffects(removal.removed, removal.affected, canReschedule),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed()
        }
    }

    fun dismissDialog() {
        plan = null
        _state.update { it.copy(dialog = null) }
    }

    fun reschedule() {
        val removal = plan ?: return
        val dialog = _state.value.dialog as? ReviewDialog.RemoveAffects ?: return
        if (_state.value.busy || !dialog.canReschedule) return
        base = removal.reorganized
        queue = removal.affected
        _state.update { it.copy(dialog = null, reschedule = stepFor(0)) }
    }

    suspend fun pickShowtime(eventId: String) {
        val step = _state.value.reschedule ?: return
        val removal = plan ?: return
        if (_state.value.busy || step.options.none { it.eventId == eventId }) return
        val ctx = checkNotNull(context).copy(now = clock())
        val current = checkNotNull(base)
        val option = rescheduleOptions(current, step.stop, events, ctx, estimator)
            .firstOrNull { it.event.id == eventId } ?: return
        val next = rescheduled(current, step.stop, option) ?: return failed()
        if (step.index + 1 < queue.size) {
            base = next
            _state.update { it.copy(reschedule = stepFor(step.index + 1), actionFailed = false) }
            return
        }
        _state.update { it.copy(busy = true, actionFailed = false) }
        try {
            // Every Stop still fits, so the draft keeps its step.
            val saved = next.copy(step = removal.itinerary.step, updatedAtMillis = clock().toEpochMilli())
            itineraries.saveItinerary(saved)
            plan = null
            base = null
            queue = emptyList()
            _state.update { it.copy(reschedule = null, busy = false) }
            publish(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed()
        }
    }

    /** Leaves the picker without saving and shows the warning again. */
    fun cancelReschedule() {
        val removal = plan ?: return
        _state.update {
            it.copy(
                reschedule = null,
                actionFailed = false,
                dialog = ReviewDialog.RemoveAffects(removal.removed, removal.affected, canReschedule = canReschedule),
            )
        }
        base = null
        queue = emptyList()
    }

    /** Removes the Stop and the affected Stops, keeps the later reachable ones, and returns to selection. */
    suspend fun reorganize() {
        val removal = plan ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, actionFailed = false, dialog = null, reschedule = null) }
        try {
            save(removal)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(dialog = ReviewDialog.RemoveAffects(removal.removed, removal.affected, canReschedule)) }
            failed()
        }
    }

    fun onNavigated() = _state.update { it.copy(navigation = null) }

    private suspend fun save(removal: StopRemovalPlan) {
        val saved = removal.reorganized.copy(updatedAtMillis = clock().toEpochMilli())
        itineraries.saveItinerary(saved)
        plan = null
        base = null
        queue = emptyList()
        _state.update {
            it.copy(
                busy = false,
                dialog = null,
                reschedule = null,
                navigation = ReviewNavigation.BackToSelection(saved.id, removal.afterStopId, removal.removed.districtId),
            )
        }
        publish(saved)
    }

    private fun stepFor(index: Int): RescheduleStep {
        val stop = queue[index]
        val ctx = checkNotNull(context).copy(now = clock())
        val options = rescheduleOptions(checkNotNull(base), stop, events, ctx, estimator).map {
            RescheduleOption(
                eventId = it.event.id,
                start = it.start.toOffsetDateTime(),
                plannedDeparture = it.suggestedDeparture.plannedDeparture.toOffsetDateTime(),
                hasPublishedEnd = it.suggestedDeparture.hasPublishedEnd,
            )
        }
        return RescheduleStep(stop, options, index, queue.size, affected = queue)
    }

    private fun failed() = _state.update { it.copy(busy = false, actionFailed = true) }
}

class ItineraryReviewViewModel(private val holder: ItineraryReviewHolder) : ViewModel() {
    val state: StateFlow<ItineraryReviewUiState> = holder.state

    init {
        retry()
    }

    fun retry() { viewModelScope.launch { holder.load() } }
    fun selectStop(id: String?) = holder.selectStop(id)
    fun start() { viewModelScope.launch { holder.start() } }
    fun remove(stopId: String) { viewModelScope.launch { holder.remove(stopId) } }
    fun reschedule() = holder.reschedule()
    fun pickShowtime(eventId: String) { viewModelScope.launch { holder.pickShowtime(eventId) } }
    fun reorganize() { viewModelScope.launch { holder.reorganize() } }
    fun confirmDialog() { viewModelScope.launch { holder.confirmDialog() } }
    fun dismissDialog() = holder.dismissDialog()
    fun cancelReschedule() = holder.cancelReschedule()
    fun onNavigated() = holder.onNavigated()
    /** Call on resume so Start reflects the current time. */
    fun onResume() = holder.refreshStart()

    companion object {
        fun factory(
            itineraryId: String,
            auth: AuthService,
            itineraries: ItineraryRepository,
            estimator: LegEstimator,
            zone: ZoneId,
            loadContext: suspend () -> PlanningContext,
            loadPrices: suspend (Itinerary) -> BudgetPrices,
            loadEvents: suspend (Itinerary) -> List<Event>,
            starter: ReviewStarter,
            demoMode: Boolean = false,
            clock: () -> Instant = Instant::now,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ItineraryReviewViewModel(
                    ItineraryReviewHolder(
                        itineraryId, { auth.currentUser?.uid }, itineraries, estimator, zone,
                        loadContext, loadPrices, loadEvents, starter, demoMode, clock,
                    ),
                )
            }
        }
    }
}
