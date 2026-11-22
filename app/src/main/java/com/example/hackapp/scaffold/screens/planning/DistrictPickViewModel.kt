package com.example.hackapp.scaffold.screens.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hackapp.auth.AuthService
import com.example.hackapp.citypack.CityPack
import com.example.hackapp.citypack.DistrictMapData
import com.example.hackapp.data.repository.EventQuery
import com.example.hackapp.data.repository.EventRepository
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryRepository
import com.example.hackapp.itinerary.PlanningStep
import com.example.hackapp.itinerary.eventEligibility
import com.example.hackapp.profile.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class DistrictPickUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val map: DistrictMapData? = null,
    val eventsByDistrict: Map<String, DistrictEvents> = emptyMap(),
    /** Chosen Districts in visit order. */
    val selected: List<String> = emptyList(),
    /** The District shown in the bottom sheet. */
    val previewId: String? = null,
    /** The selected District chosen for replacement in the maximum-reached sheet. */
    val replaceTargetId: String? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** Set once Continue has saved. The route navigates, then clears it. */
    val continueToId: String? = null,
) {
    val canContinue: Boolean get() = !loading && !loadFailed && !saving && selected.size in 1..MAX_DISTRICTS

    fun nameOf(id: String): String = map?.districts?.firstOrNull { it.id == id }?.name ?: id

    fun isPickable(id: String): Boolean {
        val shape = map?.districts?.firstOrNull { it.id == id } ?: return false
        return isDistrictPickable(shape.groupId, eventsByDistrict[id]?.count ?: 0, id in selected)
    }
}

class DistrictPickViewModel(
    private val itineraryId: String,
    private val auth: AuthService,
    private val itineraries: ItineraryRepository,
    private val events: EventRepository,
    private val cityPack: CityPack,
    private val loadMap: suspend () -> DistrictMapData,
    private val profiles: ProfileRepository,
    private val reconciler: PlanningChangeReconciler = KeepSavedStops,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(DistrictPickUiState())
    val state: StateFlow<DistrictPickUiState> = _state.asStateFlow()

    /** Serializes saves, so a slower earlier save cannot overwrite a newer selection. */
    private val saveLock = Mutex()

    init {
        load()
    }

    fun retry() = load()

    /** Picks up changes made by later Planning steps when returning to this page. */
    fun refreshSelection() {
        if (_state.value.loading || _state.value.loadFailed) return
        val uid = auth.currentUser?.uid ?: return
        val before = _state.value.selected
        viewModelScope.launch {
            try {
                val latest = saveLock.withLock { itineraries.getItinerary(uid, itineraryId) } ?: return@launch
                // A tap made during the read is newer than the stored selection.
                _state.update { if (it.selected == before) it.copy(selected = latest.districtIds) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The selection on screen stays usable, and the next save reports any storage error.
            }
        }
    }

    fun onDistrictTap(id: String) {
        if (!_state.value.isPickable(id)) return
        _state.update { it.copy(previewId = id, replaceTargetId = null) }
    }

    fun dismissSheet() = _state.update { it.copy(previewId = null, replaceTargetId = null) }

    fun addPreviewed() = changeSelection { state, id -> addDistrict(state.selected, id) }

    fun removePreviewed() = changeSelection { state, id -> removeDistrict(state.selected, id) }

    fun chooseReplaceTarget(id: String) = _state.update { it.copy(replaceTargetId = id) }

    fun confirmReplace() {
        val target = _state.value.replaceTargetId ?: return
        changeSelection { state, id -> replaceDistrict(state.selected, target, id) }
    }

    fun onNavigated() = _state.update { it.copy(continueToId = null) }

    fun continueToMeals() {
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
                saveLock.withLock {
                    val latest = checkNotNull(itineraries.getItinerary(uid, itineraryId))
                    val updated = latest.copy(
                        step = maxOf(latest.step, PlanningStep.Meals),
                        districtIds = _state.value.selected,
                        updatedAtMillis = nowMillis(),
                    )
                    val saved = if (latest.stops.isNotEmpty()) reconciler.reconcile(latest, updated) else updated
                    itineraries.saveItinerary(saved)
                }
                _state.update { it.copy(saving = false, continueToId = itineraryId) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    private fun changeSelection(change: (DistrictPickUiState, String) -> List<String>) {
        val current = _state.value
        val id = current.previewId ?: return
        val next = change(current, id)
        _state.update { it.copy(selected = next, previewId = null, replaceTargetId = null, saveFailed = false) }
        autosave()
    }

    /** Saves the newest selection and step. Stops are reconciled at Continue, not on each tap. */
    private fun autosave() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _state.update { it.copy(saveFailed = true) }
            return
        }
        viewModelScope.launch {
            try {
                saveLock.withLock {
                    val latest = checkNotNull(itineraries.getItinerary(uid, itineraryId))
                    itineraries.saveItinerary(
                        latest.copy(
                            step = maxOf(latest.step, PlanningStep.Districts),
                            districtIds = _state.value.selected,
                            updatedAtMillis = nowMillis(),
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(saveFailed = true) }
            }
        }
    }

    private fun load() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val uid = checkNotNull(auth.currentUser?.uid)
                val draft = checkNotNull(itineraries.getItinerary(uid, itineraryId))
                val map = loadMap()
                val eligibility = eventEligibility(profiles, uid, cityPack.timeZone)
                val summary = withContext(Dispatchers.Default) {
                    summarizeDistrictEvents(events.search(windowQuery(draft)), eligibility)
                }
                _state.update {
                    it.copy(
                        loading = false,
                        map = map,
                        eventsByDistrict = summary,
                        selected = draft.districtIds,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    private fun windowQuery(itinerary: Itinerary) = EventQuery(
        startsAtOrAfter = itinerary.windowStart(cityPack.timeZone).toOffsetDateTime(),
        startsBefore = itinerary.windowEnd(cityPack.timeZone).toOffsetDateTime(),
    )

    companion object {
        fun factory(
            itineraryId: String,
            auth: AuthService,
            itineraries: ItineraryRepository,
            events: EventRepository,
            cityPack: CityPack,
            loadMap: suspend () -> DistrictMapData,
            profiles: ProfileRepository,
            reconciler: PlanningChangeReconciler = KeepSavedStops,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DistrictPickViewModel(itineraryId, auth, itineraries, events, cityPack, loadMap, profiles, reconciler)
            }
        }
    }
}
