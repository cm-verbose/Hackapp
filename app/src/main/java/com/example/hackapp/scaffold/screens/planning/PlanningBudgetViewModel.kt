package com.example.hackapp.scaffold.screens.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hackapp.auth.AuthService
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryRepository
import com.example.hackapp.itinerary.MealBudgets
import com.example.hackapp.itinerary.PlanningChangePreview
import com.example.hackapp.itinerary.PlanningStep
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.Profile
import com.example.hackapp.profile.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The budgets in effect for one Itinerary. */
data class BudgetSelection(
    val lunch: BudgetBracket? = null,
    val dinner: BudgetBracket? = null,
    val events: BudgetBracket? = null,
    val lateNightEnabled: Boolean = false,
    val lateNight: BudgetBracket? = null,
) {
    val isComplete: Boolean
        get() = lunch != null && dinner != null && events != null && (!lateNightEnabled || lateNight != null)
}

/** This Itinerary's overrides, falling back to the Profile. */
fun budgetSelection(itinerary: Itinerary, profile: Profile?): BudgetSelection {
    val meals = itinerary.mealBudgets
    val lateNight = if (meals != null) meals.lateNight else profile?.lateNightBudget
    return BudgetSelection(
        lunch = meals?.lunch ?: profile?.lunchBudget,
        dinner = meals?.dinner ?: profile?.dinnerBudget,
        events = itinerary.eventBudget ?: profile?.eventBudget,
        lateNightEnabled = lateNight != null,
        lateNight = lateNight,
    )
}

/** Applies [selection] to this Itinerary only, including any Meal Blocks it already has. */
fun Itinerary.withBudgets(selection: BudgetSelection): Itinerary {
    val meals = MealBudgets(
        lunch = selection.lunch,
        dinner = selection.dinner,
        lateNight = selection.lateNight.takeIf { selection.lateNightEnabled },
    )
    return copy(
        eventBudget = selection.events,
        mealBudgets = meals,
        mealBlocks = mealBlocks.map { it.copy(budget = meals.forKind(it.kind)) },
    )
}

data class PlanningBudgetUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val selection: BudgetSelection = BudgetSelection(),
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** Set once saved. The route navigates, then clears it. */
    val continueToId: String? = null,
    /** Meal Stops that no longer fit the new budgets. Nothing is saved until [PlanningBudgetViewModel.confirmChange]. */
    val changeWarning: PlanningChangePreview? = null,
)

class PlanningBudgetViewModel(
    private val itineraryId: String,
    private val auth: AuthService,
    private val itineraries: ItineraryRepository,
    private val profiles: ProfileRepository,
    private val reconciler: PlanningChangeReconciler,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(PlanningBudgetUiState())
    val state: StateFlow<PlanningBudgetUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    /** Rereads the saved budgets without a loading state, after returning from the edit page. */
    fun refresh() {
        if (!_state.value.loading) load(showLoading = false)
    }

    fun setLunch(value: BudgetBracket?) = edit { copy(lunch = value) }
    fun setDinner(value: BudgetBracket?) = edit { copy(dinner = value) }
    fun setEvents(value: BudgetBracket?) = edit { copy(events = value) }
    fun setLateNightEnabled(value: Boolean) = edit { copy(lateNightEnabled = value) }
    fun setLateNight(value: BudgetBracket?) = edit { copy(lateNight = value) }

    fun onNavigated() = _state.update { it.copy(continueToId = null) }

    /** The edit waiting for the person to accept the Stop warning. */
    private var pendingChange: ((Itinerary) -> Itinerary)? = null

    /** Saves the budgets and the Stops that still fit, after the person accepted the warning. */
    fun confirmChange() {
        val change = pendingChange ?: return
        _state.update { it.copy(changeWarning = null) }
        save(confirmed = true, change)
    }

    /** Keeps the edited values on screen and saves nothing. */
    fun cancelChange() {
        pendingChange = null
        _state.update { it.copy(changeWarning = null) }
    }

    /** Moves on with the budgets already in effect. */
    fun keep() = save(confirmed = false) { it }

    fun saveEdits() {
        val selection = _state.value.selection
        if (selection.isComplete) save(confirmed = false) { it.withBudgets(selection) }
    }

    private fun save(confirmed: Boolean, change: (Itinerary) -> Itinerary) {
        if (_state.value.saving) return
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _state.update { it.copy(saveFailed = true) }
            return
        }
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                val latest = checkNotNull(itineraries.getItinerary(uid, itineraryId))
                val updated = change(latest).copy(
                    step = maxOf(latest.step, PlanningStep.Districts),
                    updatedAtMillis = nowMillis(),
                )
                val preview = if (latest.stops.isNotEmpty()) reconciler.preview(latest, updated) else null
                if (preview != null && preview.needsConfirmation && !confirmed) {
                    pendingChange = change
                    _state.update { it.copy(saving = false, changeWarning = preview) }
                    return@launch
                }
                pendingChange = null
                val saved = if (latest.stops.isNotEmpty()) reconciler.reconcile(latest, updated) else updated
                itineraries.saveItinerary(saved)
                _state.update { it.copy(saving = false, continueToId = itineraryId) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    private fun load(showLoading: Boolean = true) {
        if (showLoading) _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val uid = checkNotNull(auth.currentUser?.uid)
                val draft = checkNotNull(itineraries.getItinerary(uid, itineraryId))
                val selection = budgetSelection(draft, profiles.getProfile(uid))
                _state.update { it.copy(loading = false, selection = selection) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    private fun edit(change: BudgetSelection.() -> BudgetSelection) =
        _state.update { it.copy(selection = it.selection.change(), saveFailed = false) }

    companion object {
        fun factory(
            itineraryId: String,
            auth: AuthService,
            itineraries: ItineraryRepository,
            profiles: ProfileRepository,
            reconciler: PlanningChangeReconciler,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { PlanningBudgetViewModel(itineraryId, auth, itineraries, profiles, reconciler) }
        }
    }
}
