package com.example.hackapp.scaffold.screens.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hackapp.auth.AuthService
import com.example.hackapp.data.Event
import com.example.hackapp.data.model.Place
import com.example.hackapp.data.model.CuisineGroup
import com.example.hackapp.data.repository.EventQuery
import com.example.hackapp.data.repository.EventRepository
import com.example.hackapp.data.repository.PlaceRepository
import com.example.hackapp.itinerary.BudgetPrices
import com.example.hackapp.itinerary.BudgetSummary
import com.example.hackapp.itinerary.EventCard
import com.example.hackapp.itinerary.EventHourGroup
import com.example.hackapp.itinerary.EventSearch
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryRepository
import com.example.hackapp.itinerary.MealBlock
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.itinerary.MealSearch
import com.example.hackapp.itinerary.PlanningContext
import com.example.hackapp.itinerary.PlanningEngine
import com.example.hackapp.itinerary.PlanningStep
import com.example.hackapp.itinerary.ShowtimeKey
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.itinerary.TicketPriceRepository
import com.example.hackapp.itinerary.activeStops
import com.example.hackapp.itinerary.currentGap
import com.example.hackapp.itinerary.districtAfter
import com.example.hackapp.itinerary.districtBefore
import com.example.hackapp.itinerary.eventStop
import com.example.hackapp.itinerary.finishedSelection
import com.example.hackapp.itinerary.gapStart
import com.example.hackapp.itinerary.interval
import com.example.hackapp.itinerary.movedToDistrict
import com.example.hackapp.itinerary.mealBlockToAnswer
import com.example.hackapp.itinerary.selectionDistrict
import com.example.hackapp.itinerary.showtimeKey
import com.example.hackapp.itinerary.withStop
import com.example.hackapp.itinerary.withoutStop
import com.example.hackapp.itinerary.OTHER_EVENT_TYPE
import com.example.hackapp.profile.AgeRange
import com.example.hackapp.profile.Profile
import com.example.hackapp.profile.ProfileRepository
import com.example.hackapp.services.LegEstimator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The next Meal Block that still needs a Meal or a skip. [reached]: no fixed-time Event is left before it; drop-in cards are ignored. */
data class MealPrompt(
    val blockId: String,
    val kind: MealKind,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val reached: Boolean,
)

/** The open sheet. Collect the holder's `state` and forward its callbacks through the ViewModel. */
sealed interface SelectionSheet {
    class EventDetails(val holder: EventDetailsHolder) : SelectionSheet

    class PlannedDeparture(
        val holder: PlannedDepartureHolder,
        val event: Event,
        val ticketPriceCad: Double?,
        /** When the person joins a drop-in. Null for other Events. */
        val arrival: ZonedDateTime? = null,
    ) : SelectionSheet

    class MealPicker(val holder: MealPickerHolder, val block: MealBlock) : SelectionSheet
}

data class PlanningStopSelectionUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val districtIds: List<String> = emptyList(),
    val districtId: String? = null,
    /** 1-based position of [districtId] in [districtIds]. */
    val districtNumber: Int = 0,
    val hasNextDistrict: Boolean = false,
    /** At least one Event Stop exists. Enables Finish here and Finish. */
    val canFinish: Boolean = false,
    val gapStart: ZonedDateTime? = null,
    /** The next unresolved Meal Block start, else the window end. */
    val gapEnd: ZonedDateTime? = null,
    val groups: List<EventHourGroup> = emptyList(),
    val mealPrompt: MealPrompt? = null,
    val stops: List<Stop> = emptyList(),
    val budget: BudgetSummary? = null,
    val sheet: SelectionSheet? = null,
    val saveFailed: Boolean = false,
    /** Set after Finish. Navigate to Review, then call `onNavigated()`. */
    val reviewId: String? = null,
    /** Set by Back at the first District. Navigate to the previous step, then call `onNavigated()`. */
    val leaveId: String? = null,
)

/** True when no fixed-time card is left: nothing, or only drop-in cards that stay available across the block. */
fun List<EventHourGroup>.onlyDropIns(): Boolean =
    all { hour -> hour.types.all { type -> type.cards.all { it.isDropIn } } }

/** "Other" Events are hidden for the demo. Empty groups are dropped. */
fun hideOtherEvents(groups: List<EventHourGroup>): List<EventHourGroup> = groups
    .map { hour -> hour.copy(types = hour.types.filterNot { it.type.equals(OTHER_EVENT_TYPE, ignoreCase = true) }) }
    .filter { it.types.isNotEmpty() }

/** A Meal moved planning to the next District, and Back can still undo it. */
private data class MealMove(val stopId: String, val fromDistrictId: String)

/**
 * All the rules of Event and Meal selection, with no Android dependency. The ViewModel only runs
 * these on `viewModelScope`.
 */
class StopSelectionSession(
    private val itineraryId: String,
    private val uid: () -> String?,
    private val itineraries: ItineraryRepository,
    private val profiles: ProfileRepository,
    private val eventRepository: EventRepository,
    private val placeRepository: PlaceRepository,
    private val tickets: TicketPriceRepository,
    private val estimator: LegEstimator,
    private val zone: ZoneId,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val engine = PlanningEngine(estimator)
    private val lock = Mutex()
    private val _state = MutableStateFlow(PlanningStopSelectionUiState())
    val state: StateFlow<PlanningStopSelectionUiState> = _state.asStateFlow()

    private var draft: Itinerary? = null
    private var profile: Profile? = null
    private var events: List<Event> = emptyList()
    private var places: List<Place> = emptyList()
    private var cuisines: List<CuisineGroup> = emptyList()
    private val medians = mutableMapOf<ShowtimeKey, Double?>()
    private var mealMove: MealMove? = null

    suspend fun load() = lock.withLock {
        _state.update { it.copy(loading = true, loadFailed = false) }
        try {
            val id = checkNotNull(uid())
            val saved = checkNotNull(itineraries.getItinerary(id, itineraryId))
            check(saved.districtIds.isNotEmpty())
            profile = try {
                profiles.getProfile(id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            events = eventRepository.search(
                EventQuery(
                    // No lower bound: a drop-in that began before the window can still be joined.
                    startsAtOrAfter = null,
                    startsBefore = saved.windowEnd(zone).toOffsetDateTime(),
                ),
            )
            // Places only feed the Meal picker, so a failure leaves it empty instead of blocking Events.
            places = tolerant { placeRepository.allPlaces() } ?: emptyList()
            cuisines = tolerant { placeRepository.cuisineGroups() } ?: emptyList()
            draft = saved.copy(planningDistrictId = saved.selectionDistrict())
            publish()
            _state.update { it.copy(loading = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(loading = false, loadFailed = true) }
        }
    }

    suspend fun retrySave() = lock.withLock {
        val current = draft ?: return@withLock
        persist(current)
    }

    // Events

    suspend fun openEvent(card: EventCard) {
        val holder = EventDetailsHolder(card.showtimes, null, zone, uid(), tickets)
        _state.update { it.copy(sheet = SelectionSheet.EventDetails(holder)) }
        holder.load()
    }

    fun selectShowtime(eventId: String) = detailsHolder()?.selectShowtime(eventId)

    fun setTicketPriceInput(text: String) = detailsHolder()?.setTicketPriceInput(text)

    suspend fun retryEventDetails() {
        detailsHolder()?.load()
    }

    suspend fun addToItinerary() {
        val sheet = _state.value.sheet as? SelectionSheet.EventDetails ?: return
        applyAction(sheet, sheet.holder.addToItinerary())
    }

    /** After a failed price save: adds the Event without saving the typed price. Same outcomes as [addToItinerary]. */
    suspend fun addWithoutPrice() {
        val sheet = _state.value.sheet as? SelectionSheet.EventDetails ?: return
        applyAction(sheet, sheet.holder.addWithoutPrice())
    }

    private suspend fun applyAction(sheet: SelectionSheet.EventDetails, action: AddToItineraryAction?) {
        if (_state.value.sheet !== sheet) return
        when (action) {
            null -> Unit
            is AddToItineraryAction.Add -> addStop(action.stop)
            is AddToItineraryAction.ChooseDeparture -> {
                val itinerary = draft ?: return
                val latest = latestDeparture(action.event, itinerary, zone, estimator, action.arrival) ?: return
                val holder = PlannedDepartureHolder.create(action.event, itinerary, zone, latest, action.arrival) ?: return
                _state.update {
                    it.copy(sheet = SelectionSheet.PlannedDeparture(holder, action.event, action.ticketPriceCad, action.arrival))
                }
            }
        }
    }

    fun setDeparture(time: LocalTime) = departureSheet()?.holder?.setTime(time)

    suspend fun confirmDeparture() {
        val sheet = departureSheet() ?: return
        val departure = sheet.holder.confirm() ?: return
        val stop = eventStop(sheet.event, departure, sheet.ticketPriceCad, sheet.arrival) ?: return
        addStop(stop)
    }

    // Meals

    fun openMealPicker() {
        val itinerary = draft ?: return
        val district = itinerary.selectionDistrict() ?: return
        val blockId = _state.value.mealPrompt?.blockId ?: return
        val block = itinerary.mealBlocks.firstOrNull { it.id == blockId } ?: return
        val search = MealSearch(
            itinerary = itinerary,
            block = block,
            places = places,
            context = context(),
            currentDistrictId = district,
            nextDistrictId = itinerary.districtAfter(district),
            cuisineCatalog = cuisines,
        )
        _state.update { it.copy(sheet = SelectionSheet.MealPicker(MealPickerHolder(search, estimator), block)) }
    }

    fun selectMealTab(tab: MealTab) = pickerSheet()?.holder?.selectTab(tab)

    suspend fun chooseMeal(placeId: Long) {
        val sheet = pickerSheet() ?: return
        val chosen = sheet.holder.choose(placeId) ?: return
        val from = draft?.selectionDistrict() ?: return
        _state.update { it.copy(sheet = null) }
        commit(clearMove = false) { itinerary ->
            val withMeal = itinerary.withStop(chosen.stop)
            val target = chosen.moveToDistrictId
            if (target == null) {
                mealMove = null
                withMeal.copy(planningDistrictId = from)
            } else {
                // The District left stays in the list now, because Back can undo the move.
                mealMove = MealMove(chosen.stop.id, from)
                withMeal.copy(planningDistrictId = target)
            }
        }
    }

    suspend fun skipMeal() {
        val picker = pickerSheet()
        val blockId = picker?.holder?.skip()?.blockId ?: _state.value.mealPrompt?.blockId ?: return
        _state.update { it.copy(sheet = null) }
        commit { it.withMealSkipped(blockId) }
    }

    fun dismissSheet() = _state.update { it.copy(sheet = null) }

    // Moving between Districts

    suspend fun nextDistrict() {
        val itinerary = draft ?: return
        val from = itinerary.selectionDistrict() ?: return
        val target = itinerary.districtAfter(from) ?: return
        commit { it.movedToDistrict(from, target) }
    }

    suspend fun finish() {
        val itinerary = draft ?: return
        if (itinerary.finishedSelection() == null) return
        commit { it.finishedSelection() ?: it }
        if (!_state.value.saveFailed) _state.update { it.copy(reviewId = itineraryId) }
    }

    suspend fun back() {
        val itinerary = draft ?: return
        val from = itinerary.selectionDistrict() ?: return
        val move = mealMove
        if (move != null && itinerary.stops.any { it.id == move.stopId }) {
            mealMove = null
            commit { it.withoutStop(move.stopId).copy(planningDistrictId = move.fromDistrictId) }
            return
        }
        val before = itinerary.districtBefore(from)
        if (before == null) {
            _state.update { it.copy(leaveId = itineraryId) }
            return
        }
        commit { it.movedToDistrict(from, before) }
    }

    fun onNavigated() = _state.update { it.copy(reviewId = null, leaveId = null) }

    // Internals

    private suspend fun addStop(stop: Stop) {
        _state.update { it.copy(sheet = null) }
        commit { it.withStop(stop) }
    }

    /** Applies [transform] to the draft, saves it and recomputes the list. */
    private suspend fun commit(clearMove: Boolean = true, transform: (Itinerary) -> Itinerary) {
        lock.withLock {
            val base = draft ?: return
            if (clearMove) mealMove = null
            val next = transform(base).let {
                it.copy(
                    step = maxOf(it.step, PlanningStep.Stops),
                    planningDistrictId = it.planningDistrictId ?: it.selectionDistrict(),
                    updatedAtMillis = nowMillis(),
                )
            }
            draft = next
            persist(next)
            publish()
        }
    }

    private suspend fun persist(itinerary: Itinerary) {
        try {
            itineraries.saveItinerary(itinerary)
            _state.update { it.copy(saveFailed = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(saveFailed = true) }
        }
    }

    private suspend fun publish() {
        val itinerary = draft ?: return
        val district = itinerary.selectionDistrict() ?: return
        loadMedians(district)
        val context = context()
        val now = context.now
        val pending = itinerary.mealBlockToAnswer(now, zone)
        val pendingStart = pending?.interval(itinerary, zone)?.start
        val search = EventSearch(
            itinerary = itinerary,
            events = events,
            context = context,
            districtId = district,
            gap = itinerary.currentGap(),
            medianPrices = medians.mapNotNull { (key, value) -> value?.let { key to it } }.toMap(),
            until = pendingStart,
        )
        val groups = hideOtherEvents(engine.groupedEvents(search))
        val placesById = places.associateBy { it.id }
        val prices = BudgetPrices(
            eventsById = events.associateBy { it.id },
            medianPrices = search.medianPrices,
            mealPrices = itinerary.activeStops
                .filter { it.kind == StopKind.Meal }
                .mapNotNull { it.sourceId.toLongOrNull() }
                .associateWith { placesById[it]?.price },
        )
        val index = itinerary.districtIds.indexOf(district)
        _state.update {
            it.copy(
                districtIds = itinerary.districtIds,
                districtId = district,
                districtNumber = index + 1,
                hasNextDistrict = itinerary.districtAfter(district) != null,
                canFinish = engine.canFinish(itinerary),
                gapStart = itinerary.gapStart(now, zone).atZone(zone),
                gapEnd = (pendingStart ?: itinerary.windowEnd(zone).toInstant()).atZone(zone),
                groups = groups,
                mealPrompt = pending?.let { block ->
                    val span = block.interval(itinerary, zone)
                    MealPrompt(block.id, block.kind, span.start.atZone(zone), span.end.atZone(zone), groups.onlyDropIns())
                },
                stops = itinerary.activeStops,
                budget = engine.budget(itinerary, context, prices),
            )
        }
    }

    /** Shared median prices for this District's Events. A failed read leaves the price unknown. */
    private suspend fun loadMedians(districtId: String) {
        events.filter { it.districtId == districtId }.map { it.showtimeKey() }.distinct()
            .filter { it !in medians }
            .forEach { key -> medians[key] = tolerant { tickets.getSummary(key)?.medianCad } }
    }

    private fun context(): PlanningContext {
        val now = Instant.ofEpochMilli(nowMillis())
        return profile?.let { PlanningContext.from(it, now, zone) } ?: PlanningContext(now, zone, AgeRange.From25To34)
    }

    private fun detailsHolder() = (_state.value.sheet as? SelectionSheet.EventDetails)?.holder

    private fun departureSheet() = _state.value.sheet as? SelectionSheet.PlannedDeparture

    private fun pickerSheet() = _state.value.sheet as? SelectionSheet.MealPicker

    private suspend fun <T> tolerant(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

class PlanningStopSelectionViewModel(private val session: StopSelectionSession) : ViewModel() {
    val state: StateFlow<PlanningStopSelectionUiState> = session.state

    init {
        launchOn { session.load() }
    }

    fun retry() = launchOn { session.load() }
    fun retrySave() = launchOn { session.retrySave() }
    fun openEvent(card: EventCard) = launchOn { session.openEvent(card) }
    fun selectShowtime(eventId: String) = session.selectShowtime(eventId)
    fun setTicketPriceInput(text: String) = session.setTicketPriceInput(text)
    fun retryEventDetails() = launchOn { session.retryEventDetails() }
    fun addToItinerary() = launchOn { session.addToItinerary() }
    fun addWithoutPrice() = launchOn { session.addWithoutPrice() }
    fun setDeparture(time: LocalTime) = session.setDeparture(time)
    fun confirmDeparture() = launchOn { session.confirmDeparture() }
    fun openMealPicker() = session.openMealPicker()
    fun selectMealTab(tab: MealTab) = session.selectMealTab(tab)
    fun chooseMeal(placeId: Long) = launchOn { session.chooseMeal(placeId) }
    fun skipMeal() = launchOn { session.skipMeal() }
    fun dismissSheet() = session.dismissSheet()
    fun nextDistrict() = launchOn { session.nextDistrict() }
    fun finish() = launchOn { session.finish() }
    fun back() = launchOn { session.back() }
    fun onNavigated() = session.onNavigated()

    private fun launchOn(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        fun factory(
            itineraryId: String,
            auth: AuthService,
            itineraries: ItineraryRepository,
            profiles: ProfileRepository,
            events: EventRepository,
            places: PlaceRepository,
            tickets: TicketPriceRepository,
            estimator: LegEstimator,
            zone: ZoneId,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PlanningStopSelectionViewModel(
                    StopSelectionSession(
                        itineraryId, { auth.currentUser?.uid }, itineraries, profiles, events, places, tickets,
                        estimator, zone,
                    ),
                )
            }
        }
    }
}
