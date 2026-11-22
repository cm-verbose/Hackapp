package com.example.hackapp.scaffold.screens.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hackapp.data.Event
import com.example.hackapp.data.repository.EventQuery
import com.example.hackapp.data.repository.EventRepository
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryRepository
import com.example.hackapp.itinerary.StopStatus
import com.example.hackapp.itinerary.eventEligibility
import com.example.hackapp.profile.ProfileRepository
import com.example.hackapp.scaffold.screens.itinerary.ItineraryCardUi
import com.example.hackapp.scaffold.screens.itinerary.eventStopType
import com.example.hackapp.scaffold.screens.itinerary.openRoute
import com.example.hackapp.scaffold.screens.itinerary.stopTypesFor
import com.example.hackapp.scaffold.screens.itinerary.toCard
import com.example.hackapp.scaffold.screens.itinerary.toSections
import com.example.hackapp.scaffold.screens.onboarding.LoadState
import com.example.hackapp.ui.components.HackappIcon
import com.example.hackapp.ui.components.StripStop
import com.example.hackapp.ui.components.icon
import com.example.hackapp.ui.theme.StopType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Immutable
data class UnderwayUi(
    val date: LocalDate,
    val districtName: String?,
    val nextStopName: String?,
    val strip: List<StripStop>,
    val currentIndex: Int?,
    val route: Any,
)

@Immutable
data class EventSuggestion(val id: String, val title: String, val icon: HackappIcon)

data class HomeItineraries(val underway: UnderwayUi?, val planning: List<ItineraryCardUi>)

data class HomeUiState(
    val itineraries: LoadState<HomeItineraries> = LoadState.Loading,
    val firstName: String? = null,
    val suggestions: List<EventSuggestion> = emptyList(),
)

private val StopTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** The first word of the saved name, or null when there is none. */
internal fun firstNameOf(name: String?): String? =
    name?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }

/** Up to [limit] upcoming Events. Repeated showtimes of one title at one venue count once. */
internal fun suggestionsFrom(events: List<Event>, limit: Int = MAX_SUGGESTIONS): List<EventSuggestion> =
    events.distinctBy { it.title to it.location.venueName }
        .take(limit)
        .map { EventSuggestion(it.id, it.title, eventStopType(it.type).icon) }

private const val MAX_SUGGESTIONS = 3

internal fun Itinerary.toUnderwayUi(
    stopTypes: Map<String, StopType>,
    districtNames: Map<String, String>,
    zone: ZoneId,
): UnderwayUi {
    fun time(value: OffsetDateTime) = value.atZoneSameInstant(zone).format(StopTimeFormat)
    val nextIndex = stops.indexOfFirst { it.status == StopStatus.Planned }
    val next = stops.getOrNull(nextIndex)
    return UnderwayUi(
        date = date,
        districtName = (districtIds.firstOrNull() ?: stops.firstOrNull()?.districtId)?.let(districtNames::get),
        nextStopName = next?.name,
        strip = stops.map { StripStop(time(it.start), stopTypes[it.id] ?: StopType.Festival) },
        currentIndex = nextIndex.takeIf { it >= 0 },
        route = openRoute(),
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val uid: String?,
    private val itineraries: ItineraryRepository,
    private val profiles: ProfileRepository,
    private val events: EventRepository,
    private val zone: ZoneId,
    private val loadDistrictNames: suspend () -> Map<String, String>,
) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val firstName = MutableStateFlow<String?>(null)
    private val suggestions = MutableStateFlow<List<EventSuggestion>>(emptyList())
    private val districtNames = MutableStateFlow<Map<String, String>>(emptyMap())

    private val saved = retry.flatMapLatest {
        if (uid == null) {
            flowOf<LoadState<List<Itinerary>>>(LoadState.Ready(emptyList()))
        } else {
            itineraries.observeItineraries(uid)
                .map<_, LoadState<List<Itinerary>>> { LoadState.Ready(it) }
                .onStart { emit(LoadState.Loading) }
                .catch { emit(LoadState.Failed) }
        }
    }

    private val content = combine(saved, districtNames) { load, names -> load to names }
        .mapLatest { (load, names) ->
            when (load) {
                LoadState.Loading -> LoadState.Loading
                LoadState.Failed -> LoadState.Failed
                is LoadState.Ready -> {
                    val sections = load.value.toSections()
                    val underway = sections.underway?.let {
                        it.toUnderwayUi(events.stopTypesFor(it.stops), names, zone)
                    }
                    LoadState.Ready(HomeItineraries(underway, sections.planning.map { it.toCard(names) }))
                }
            }
        }

    val state: StateFlow<HomeUiState> = combine(content, firstName, suggestions) { list, name, suggested ->
        HomeUiState(itineraries = list, firstName = name, suggestions = suggested)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    init {
        // The greeting, suggestions and names are optional: a failure just leaves them out.
        viewModelScope.launch {
            firstName.value = optional { uid?.let { firstNameOf(profiles.getProfile(it)?.name) } }
        }
        viewModelScope.launch {
            suggestions.value = optional {
                val eligible = eventEligibility(profiles, uid, zone)
                suggestionsFrom(events.search(EventQuery(startsAtOrAfter = OffsetDateTime.now(zone))).filter(eligible))
            }.orEmpty()
        }
        viewModelScope.launch { districtNames.value = loadDistrictNames() }
    }

    fun retry() = retry.update { it + 1 }

    private suspend fun <T> optional(read: suspend () -> T?): T? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(
            uid: String?,
            itineraries: ItineraryRepository,
            profiles: ProfileRepository,
            events: EventRepository,
            zone: ZoneId,
            loadDistrictNames: suspend () -> Map<String, String>,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(uid, itineraries, profiles, events, zone, loadDistrictNames) }
        }
    }
}
