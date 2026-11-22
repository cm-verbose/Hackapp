package com.example.hackapp.scaffold.screens.planning

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hackapp.auth.AuthService
import com.example.hackapp.citypack.CityPack
import com.example.hackapp.citypack.EventCoverage
import com.example.hackapp.data.parseEventTime
import com.example.hackapp.data.repository.EventRepository
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.ItineraryRepository
import com.example.hackapp.itinerary.OutingKind
import com.example.hackapp.itinerary.PlanningChangePreview
import com.example.hackapp.itinerary.PlanningStep
import com.example.hackapp.itinerary.withFittingMealBlocks
import com.example.hackapp.profile.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class PlanningDateTimeUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val coverage: EventCoverage,
    val date: LocalDate = coverage.firstDate,
    val outingKind: OutingKind = OutingKind.Night,
    val start: LocalTime = outingKind.defaultStart(),
    val end: LocalTime = outingKind.defaultEnd(),
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** Set once the draft is saved. The route navigates, then clears it. */
    val savedItineraryId: String? = null,
    /** Stops that no longer fit the new date or time. Nothing is saved until [PlanningDateTimeViewModel.confirmChange]. */
    val changeWarning: PlanningChangePreview? = null,
) {
    val windowError: TimeWindowError? get() = validateTimeWindow(start, end)
    val canContinue: Boolean get() = !loading && !loadFailed && !saving && windowError == null
}

class PlanningDateTimeViewModel(
    private val savedState: SavedStateHandle,
    private val routeItineraryId: String?,
    private val auth: AuthService,
    private val itineraries: ItineraryRepository,
    private val profiles: ProfileRepository,
    private val events: EventRepository,
    private val cityPack: CityPack,
    private val reconciler: PlanningChangeReconciler,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(PlanningDateTimeUiState(coverage = cityPack.eventCoverage))
    val state: StateFlow<PlanningDateTimeUiState> = _state.asStateFlow()

    /** The draft this page edits. Kept so Back then Continue never creates a second draft. */
    private val draftId: String? get() = savedState.get<String>(DRAFT_ID_KEY) ?: routeItineraryId

    init {
        load()
    }

    fun retry() = load()

    fun setDate(value: LocalDate) = edit { copy(date = value) }

    /** Switching between day and night resets the times. Tapping the current one keeps them. */
    fun setOutingKind(value: OutingKind) {
        if (value == _state.value.outingKind) return
        edit { copy(outingKind = value, start = value.defaultStart(), end = value.defaultEnd()) }
    }

    fun setStart(value: LocalTime) = edit { copy(start = value) }
    fun setEnd(value: LocalTime) = edit { copy(end = value) }

    fun onNavigated() = _state.update { it.copy(savedItineraryId = null) }

    fun save() = save(confirmed = false)

    /** Saves the edit and the Stops that fit, after the person accepted the warning. */
    fun confirmChange() {
        _state.update { it.copy(changeWarning = null) }
        save(confirmed = true)
    }

    /** Keeps the page as it is and saves nothing. */
    fun cancelChange() = _state.update { it.copy(changeWarning = null) }

    private fun save(confirmed: Boolean) {
        val current = _state.value
        if (!current.canContinue) return
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _state.update { it.copy(saveFailed = true) }
            return
        }
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                val saved = persist(uid, current, confirmed)
                if (saved == null) {
                    _state.update { it.copy(saving = false) }
                    return@launch
                }
                savedState[DRAFT_ID_KEY] = saved.id
                _state.update { it.copy(saving = false, savedItineraryId = saved.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    /**
     * Reads the stored draft first, because later steps may have changed it. Returns null, saving
     * nothing, when Stops would be lost and the person has not confirmed.
     */
    private suspend fun persist(uid: String, form: PlanningDateTimeUiState, confirmed: Boolean): Itinerary? {
        val now = nowMillis()
        val existingId = draftId
        val saved = if (existingId == null) {
            Itinerary(
                id = UUID.randomUUID().toString(),
                ownerUid = uid,
                cityId = cityPack.id,
                step = PlanningStep.Budget,
                date = form.date,
                outingKind = form.outingKind,
                startTime = form.start,
                endTime = form.end,
                eventBudget = profileEventBudget(uid),
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        } else {
            val latest = checkNotNull(itineraries.getItinerary(uid, existingId))
            val updated = latest.copy(
                // Choosing a new date is what brings an archived draft back to Planning.
                phase = if (latest.phase == ItineraryPhase.Archived) ItineraryPhase.Planning else latest.phase,
                step = maxOf(latest.step, PlanningStep.Budget),
                date = form.date,
                outingKind = form.outingKind,
                startTime = form.start,
                endTime = form.end,
                updatedAtMillis = now,
            )
            val windowChanged = latest.date != updated.date ||
                latest.startTime != updated.startTime ||
                latest.endTime != updated.endTime
            val fitted = if (windowChanged) updated.withFittingMealBlocks(cityPack.timeZone) else updated
            if (windowChanged && latest.stops.isNotEmpty()) {
                val preview = reconciler.preview(latest, fitted)
                if (preview.needsConfirmation && !confirmed) {
                    _state.update { it.copy(changeWarning = preview) }
                    return null
                }
                reconciler.reconcile(latest, fitted)
            } else {
                fitted
            }
        }
        itineraries.saveItinerary(saved)
        return saved
    }

    private fun load() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val uid = checkNotNull(auth.currentUser?.uid)
                val id = draftId
                if (id != null) {
                    val draft = checkNotNull(itineraries.getItinerary(uid, id))
                    _state.update {
                        it.copy(
                            loading = false,
                            date = draft.date,
                            outingKind = draft.outingKind,
                            start = draft.startTime,
                            end = draft.endTime,
                        )
                    }
                } else {
                    val date = firstDateWithData()
                    _state.update { it.copy(loading = false, date = date) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    /** A missing or unreadable Profile leaves the budget empty instead of blocking Planning. */
    private suspend fun profileEventBudget(uid: String) = try {
        profiles.getProfile(uid)?.eventBudget
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun firstDateWithData(): LocalDate {
        val zone = cityPack.timeZone
        val today = LocalDate.now(zone)
        val dates = try {
            withContext(Dispatchers.Default) {
                events.search().mapNotNullTo(HashSet()) { event ->
                    parseEventTime(event.schedule.startDate)?.atZoneSameInstant(zone)?.toLocalDate()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptySet()
        }
        return defaultPlanningDate(today, cityPack.eventCoverage, dates)
    }

    private fun edit(change: PlanningDateTimeUiState.() -> PlanningDateTimeUiState) =
        _state.update { it.change().copy(saveFailed = false) }

    companion object {
        private const val DRAFT_ID_KEY = "planning_draft_id"

        fun factory(
            routeItineraryId: String?,
            auth: AuthService,
            itineraries: ItineraryRepository,
            profiles: ProfileRepository,
            events: EventRepository,
            cityPack: CityPack,
            reconciler: PlanningChangeReconciler = cityPack.stopFitReconciler(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PlanningDateTimeViewModel(
                    createSavedStateHandle(),
                    routeItineraryId,
                    auth,
                    itineraries,
                    profiles,
                    events,
                    cityPack,
                    reconciler,
                )
            }
        }
    }
}
