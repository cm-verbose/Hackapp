package com.example.hackapp.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.hackapp.HackappApplication
import com.example.hackapp.data.model.PriceBracket
import com.example.hackapp.services.CityServices
import com.example.hackapp.services.UnderwayNotifier
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State shared by the onboarding, planning and underway screens, scoped to the activity so the
 * draft Itinerary survives moving between pages. Rules live in [ItineraryPlanner].
 */
class FlowViewModel(
    private val app: HackappApplication,
    val services: CityServices,
) : ViewModel() {
    private val zone = services.cityPack.timeZone
    val profileStore: ProfileStore = app.profileStore
    private val underwayStore: UnderwayStore = app.underwayStore
    val notifier: UnderwayNotifier = app.notifier

    val planner = ItineraryPlanner(services.events, { services.places }, services.legs, zone)

    /** Null until DataStore has answered, so screens can wait instead of flashing onboarding. */
    val profile: StateFlow<UserProfile?> =
        profileStore.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val underway: StateFlow<UnderwayProgress?> =
        underwayStore.progress.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val stamps: StateFlow<List<EarnedStamp>> =
        underwayStore.stamps.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Draft Itinerary (Planning) ----
    var kind by mutableStateOf(ItineraryKind.DAY_OUT)
        private set
    var window by mutableStateOf(PlanWindow.default(FIRST_DATE, ItineraryKind.DAY_OUT))
        private set
    var budgetOverride by mutableStateOf<String?>(null)
        private set
    var districts by mutableStateOf<List<String>>(emptyList())
        private set
    var wantsMeal by mutableStateOf(true)
        private set
    var meal by mutableStateOf<MealBlock?>(null)
        private set
    val stops = mutableStateListOf<Stop>()
    /** Index in [districts] of the District whose Stops are being chosen. */
    var districtIndex by mutableStateOf(0)
        private set
    val mealChosen get() = stops.any { it.isMeal }

    fun newDraft() {
        kind = ItineraryKind.DAY_OUT
        window = PlanWindow.default(FIRST_DATE, kind)
        budgetOverride = null
        districts = emptyList()
        wantsMeal = true
        meal = null
        stops.clear()
        districtIndex = 0
    }

    fun pickKind(k: ItineraryKind) {
        kind = k
        window = PlanWindow.default(window.date, k)
    }

    fun setDate(date: LocalDate) { window = window.copy(date = date) }
    fun setStart(t: LocalTime) { window = window.copy(start = t) }
    fun setEnd(t: LocalTime) { window = window.copy(end = t) }
    fun pickBudget(code: String?) { budgetOverride = code }

    fun toggleDistrict(id: String) {
        districts = when {
            id in districts -> districts - id
            districts.size < MAX_DISTRICTS -> districts + id
            else -> districts
        }
    }

    fun pickWantsMeal(v: Boolean) { wantsMeal = v; if (!v) meal = null }
    fun chooseMeal(block: MealBlock?) { meal = block }

    /** Called when StopSelection opens fresh: earlier choices may not fit the new Districts or meal. */
    fun resetSelection() {
        stops.clear()
        districtIndex = 0
    }

    fun addStop(stop: Stop) { stops.add(stop); stops.sortBy { it.start } }

    /** Removing a Stop drops the later ones too: choosing resumes from that time gap. */
    fun removeStop(index: Int) {
        while (stops.size > index) stops.removeAt(stops.lastIndex)
        districtIndex = districts.indexOf(stops.lastOrNull()?.districtId).coerceAtLeast(0)
    }

    fun nextDistrict() { if (districtIndex < districts.lastIndex) districtIndex++ }

    suspend fun brackets(): List<PriceBracket> = services.places.priceBrackets()

    /** The input rules need: window, audience and the Itinerary or profile budget as a price limit. */
    suspend fun planInput(): PlanInput {
        val p = profile.value ?: UserProfile()
        val code = budgetOverride ?: p.budgetCode
        val max = code?.let { c -> brackets().firstOrNull { it.code == c }?.maxPrice }
        return PlanInput(window, p.audience, max, p)
    }

    fun draft() = Itinerary(window.date.toString(), districts, stops.toList())

    // ---- Underway ----
    fun start(rideHail: Boolean = false, fromLat: Double? = null, fromLng: Double? = null) {
        val progress = UnderwayProgress(draft(), useRideHail = rideHail, fromLat = fromLat, fromLng = fromLng)
        viewModelScope.launch {
            underwayStore.save(progress)
            if (notifier.hasPermission()) notifier.schedule(progress.itinerary)
        }
    }

    fun setStartFrom(lat: Double?, lng: Double?) {
        viewModelScope.launch { underwayStore.update { it.copy(fromLat = lat, fromLng = lng) } }
    }

    fun setRideHail(v: Boolean) {
        viewModelScope.launch { underwayStore.update { it.copy(useRideHail = v) } }
    }

    fun arrived() { viewModelScope.launch { underwayStore.update { it.arrived() } } }

    /** Leaves the current Stop and skips the next one (late-arrival warning). */
    fun leaveAndSkipNext() {
        viewModelScope.launch {
            underwayStore.update { it.finishCurrent(StopStatus.CONFIRMED).finishCurrent(StopStatus.SKIPPED) }
        }
    }

    fun finishStop(status: StopStatus) {
        viewModelScope.launch { underwayStore.update { it.finishCurrent(status) } }
    }

    /** Ends the day: confirmed Stops become Stamps, notifications are cancelled, progress is cleared. */
    fun endDay(onDone: () -> Unit) {
        viewModelScope.launch {
            val p = underwayStore.current()
            if (p != null) {
                underwayStore.addStamps(newStamps(p))
                notifier.cancel(p.itinerary)
            }
            underwayStore.clear()
            onDone()
        }
    }

    /** Stamps this underway progress will give: confirmed Stops only. */
    fun newStamps(p: UnderwayProgress): List<EarnedStamp> =
        p.itinerary.stops.filterIndexed { i, _ -> p.statuses.getOrNull(i) == StopStatus.CONFIRMED }
            .map { EarnedStamp(it.id, it.title, it.districtId, p.itinerary.date) }

    /** When the person is expected at stop [i]: the previous departure plus the Leg, else its start. */
    fun expectedArrival(p: UnderwayProgress, i: Int): LocalDateTime = p.itinerary.stops[i].start

    class Factory(private val app: HackappApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FlowViewModel(app, app.cityServices) as T
    }

    companion object {
        const val MAX_DISTRICTS = 3
        /** First and last day of the bundled Event snapshot. */
        val FIRST_DATE: LocalDate = LocalDate.of(2026, 11, 21)
        val LAST_DATE: LocalDate = LocalDate.of(2026, 12, 6)
    }
}
