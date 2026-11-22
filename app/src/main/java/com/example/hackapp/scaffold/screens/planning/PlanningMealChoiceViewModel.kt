package com.example.hackapp.scaffold.screens.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hackapp.auth.AuthService
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryRepository
import com.example.hackapp.itinerary.MealBlock
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.itinerary.PlanningContext
import com.example.hackapp.itinerary.PlanningStep
import com.example.hackapp.itinerary.defaultBlockId
import com.example.hackapp.itinerary.defaultStart
import com.example.hackapp.itinerary.fitsWindow
import com.example.hackapp.itinerary.interval
import com.example.hackapp.itinerary.mealBudget
import com.example.hackapp.itinerary.planningWindow
import com.example.hackapp.itinerary.reconcileMealStops
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.Profile
import com.example.hackapp.profile.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** [OutsideWindow]: the block leaves the Itinerary window. [Overlaps]: it overlaps another selected block. */
enum class MealTimeError { OutsideWindow, Overlaps }

/** What the person has set for one Meal. Unselected picks keep their edits. */
data class MealPick(
    /** True when the Meal can be offered: its default block fits, or the draft already had it. */
    val offered: Boolean,
    val selected: Boolean,
    val start: LocalTime,
    /** Null means no bracket chosen, so the Itinerary or Profile budget applies. */
    val budget: BudgetBracket? = null,
)

data class MealChoiceOption(
    val kind: MealKind,
    val selected: Boolean,
    val start: LocalTime,
    /** [start] plus the block length. May be after midnight. */
    val endTime: LocalTime,
    val budget: BudgetBracket?,
    /** The bracket search uses: [budget], else the Itinerary override, else the Profile. */
    val effectiveBudget: BudgetBracket?,
    val error: MealTimeError?,
)

/** The picks a draft opens with: its saved blocks, or every default block that fits (and is not an off add-on) when unanswered. */
fun initialMealPicks(itinerary: Itinerary, context: PlanningContext): Map<MealKind, MealPick> =
    MealKind.entries.associateWith { kind ->
        val saved = itinerary.mealBlocks.firstOrNull { it.kind == kind }
        val default = MealBlock(kind.defaultBlockId(), kind, kind.defaultStart)
        val fits = default.fitsWindow(itinerary, context.zone)
        // The Late bite add-on is off without a budget: it stays offered but is not preselected.
        val addOnOn = kind != MealKind.LateBite || itinerary.mealBudget(kind, context) != null
        when {
            saved != null -> MealPick(offered = true, selected = true, start = saved.start, budget = saved.budget)
            else -> MealPick(
                offered = fits,
                selected = fits && addOnOn && itinerary.planMeals == null,
                start = kind.defaultStart,
            )
        }
    }

/** Offered Meals with their errors. Empty when no Meal fits the window. */
fun mealChoiceOptions(
    itinerary: Itinerary,
    picks: Map<MealKind, MealPick>,
    context: PlanningContext,
): List<MealChoiceOption> {
    val zone = context.zone
    val window = itinerary.planningWindow(zone)
    val selected = MealKind.entries.filter { picks[it]?.selected == true }
    val intervals = selected.associateWith { MealBlock("", it, picks.getValue(it).start).interval(itinerary, zone) }
    return MealKind.entries.mapNotNull { kind ->
        val pick = picks[kind]?.takeIf { it.offered } ?: return@mapNotNull null
        val error = intervals[kind]?.let { own ->
            when {
                !window.encloses(own) -> MealTimeError.OutsideWindow
                intervals.any { (other, span) -> other != kind && own.overlaps(span.start, span.end) } ->
                    MealTimeError.Overlaps
                else -> null
            }
        }
        MealChoiceOption(
            kind = kind,
            selected = pick.selected,
            start = pick.start,
            endTime = pick.start.plusMinutes(kind.blockMinutes.toLong()),
            budget = pick.budget,
            effectiveBudget = pick.budget ?: itinerary.mealBudget(kind, context),
            error = error,
        )
    }
}

/** Applies the answer to [itinerary]. Declining Meals reserves nothing. Blocks are kept in window order. */
fun Itinerary.withMealChoice(planMeals: Boolean, picks: Map<MealKind, MealPick>, zone: ZoneId): Itinerary {
    val blocks = if (!planMeals) emptyList() else {
        MealKind.entries
            .filter { picks[it]?.selected == true }
            .map { kind ->
                val pick = picks.getValue(kind)
                val existing = mealBlocks.firstOrNull { it.kind == kind }
                MealBlock(
                    id = existing?.id ?: kind.defaultBlockId(),
                    kind = kind,
                    start = pick.start,
                    budget = pick.budget,
                    skipped = existing?.skipped == true,
                )
            }
            .sortedBy { it.interval(this, zone).start }
    }
    return copy(planMeals = planMeals, mealBlocks = blocks)
}


data class PlanningMealChoiceUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val planMeals: Boolean = true,
    /** The Itinerary window, set when the draft loads. Null until then. */
    val windowStart: LocalTime? = null,
    val windowEnd: LocalTime? = null,
    val options: List<MealChoiceOption> = emptyList(),
    /** No Meal block fits the Itinerary window. */
    val noMealFits: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** Set once saved. The route navigates, then clears it with `onNavigated()`. */
    val continueToId: String? = null,
    /** Set by `changeDateAndTime()`. The route navigates to Date and time, then clears it. */
    val changeDateTimeId: String? = null,
) {
    val canContinue: Boolean
        get() = !loading && !loadFailed && !saving && when {
            !planMeals -> true
            noMealFits -> false
            else -> options.any { it.selected } && options.none { it.selected && it.error != null }
        }
}

/** Removes Meal Stops whose block is gone or moved. Event Stops stay. */
val MealStopReconciler = PlanningChangeReconciler { previous, updated -> reconcileMealStops(previous, updated) }

class PlanningMealChoiceViewModel(
    private val itineraryId: String,
    private val auth: AuthService,
    private val itineraries: ItineraryRepository,
    private val profiles: ProfileRepository,
    private val zone: ZoneId,
    private val reconciler: PlanningChangeReconciler = MealStopReconciler,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(PlanningMealChoiceUiState())
    val state: StateFlow<PlanningMealChoiceUiState> = _state.asStateFlow()

    private var draft: Itinerary? = null
    private var profile: Profile? = null
    private var picks: Map<MealKind, MealPick> = emptyMap()

    init {
        load()
    }

    fun retry() = load()

    fun setPlanMeals(value: Boolean) = edit { copy(planMeals = value) }

    fun toggleMeal(kind: MealKind) = editPick(kind) { if (offered) copy(selected = !selected) else this }

    fun setStart(kind: MealKind, start: LocalTime) = editPick(kind) { copy(start = start) }

    fun setBudget(kind: MealKind, budget: BudgetBracket?) = editPick(kind) { copy(budget = budget) }

    fun changeDateAndTime() = _state.update { it.copy(changeDateTimeId = itineraryId) }

    fun onNavigated() = _state.update { it.copy(continueToId = null, changeDateTimeId = null) }

    fun continueToStops() {
        val current = _state.value
        if (!current.canContinue) return
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _state.update { it.copy(saveFailed = true) }
            return
        }
        val chosen = picks
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                val latest = checkNotNull(itineraries.getItinerary(uid, itineraryId))
                val updated = latest.withMealChoice(current.planMeals, chosen, zone).copy(
                    step = maxOf(latest.step, PlanningStep.Stops),
                    updatedAtMillis = nowMillis(),
                )
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

    private fun load() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val uid = checkNotNull(auth.currentUser?.uid)
                val saved = checkNotNull(itineraries.getItinerary(uid, itineraryId))
                draft = saved
                profile = checkNotNull(profiles.getProfile(uid))
                picks = initialMealPicks(saved, planningContext(checkNotNull(profile)))
                _state.update {
                    it.copy(
                        loading = false,
                        planMeals = saved.planMeals ?: true,
                        windowStart = saved.startTime,
                        windowEnd = saved.endTime,
                    )
                }
                publish { it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    private fun planningContext(profile: Profile) =
        PlanningContext.from(profile, Instant.ofEpochMilli(nowMillis()), zone)

    private fun editPick(kind: MealKind, change: MealPick.() -> MealPick) {
        val pick = picks[kind] ?: return
        picks = picks + (kind to pick.change())
        edit { this }
    }

    private fun edit(change: PlanningMealChoiceUiState.() -> PlanningMealChoiceUiState) =
        publish { it.change().copy(saveFailed = false) }

    /** Recomputes the options from [picks] after applying [change]. */
    private fun publish(change: (PlanningMealChoiceUiState) -> PlanningMealChoiceUiState) {
        val itinerary = draft ?: return
        val profile = profile ?: return
        val context = planningContext(profile)
        val options = mealChoiceOptions(itinerary, picks, context)
        _state.update { change(it).copy(options = options, noMealFits = options.isEmpty()) }
    }

    companion object {
        fun factory(
            itineraryId: String,
            auth: AuthService,
            itineraries: ItineraryRepository,
            profiles: ProfileRepository,
            zone: ZoneId,
            reconciler: PlanningChangeReconciler = MealStopReconciler,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { PlanningMealChoiceViewModel(itineraryId, auth, itineraries, profiles, zone, reconciler) }
        }
    }
}
