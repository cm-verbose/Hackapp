package com.example.hackapp.scaffold.screens.itinerary

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hackapp.citypack.GeoPoint
import com.example.hackapp.data.repository.EventRepository
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.ItineraryRepository
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.itinerary.StopStatus
import com.example.hackapp.scaffold.screens.onboarding.LoadState
import com.example.hackapp.ui.components.map.RouteStop
import com.example.hackapp.ui.theme.StopType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Immutable
data class DetailStop(
    val id: String,
    val time: String,
    val name: String,
    val kind: StopKind,
    val districtName: String,
    val status: StopStatus,
)

@Immutable
data class DetailStamp(val id: String, val name: String, val districtName: String)

@Immutable
data class ItineraryDetail(
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val districtName: String?,
    val stops: List<DetailStop>,
    val routeStops: List<RouteStop>,
    val stamps: List<DetailStamp>,
)

/** [detail] holds null when no Itinerary of the expected phase exists. */
data class ItineraryDetailUiState(
    val detail: LoadState<ItineraryDetail?> = LoadState.Loading,
    val confirmingDelete: Boolean = false,
    val deleteFailed: Boolean = false,
    val deleted: Boolean = false,
)

private val StopTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

internal fun Itinerary.toDetail(
    stopTypes: Map<String, StopType>,
    districtNames: Map<String, String>,
    zone: ZoneId,
): ItineraryDetail {
    fun districtName(id: String) = districtNames[id] ?: id
    return ItineraryDetail(
        date = date,
        startTime = startTime,
        endTime = endTime,
        districtName = (districtIds.firstOrNull() ?: stops.firstOrNull()?.districtId)?.let(districtNames::get),
        stops = stops.map { stop ->
            DetailStop(
                id = stop.id,
                time = stop.start.atZoneSameInstant(zone).format(StopTimeFormat),
                name = stop.name,
                kind = stop.kind,
                districtName = districtName(stop.districtId),
                status = stop.status,
            )
        },
        routeStops = stops.mapIndexed { index, stop ->
            RouteStop(
                order = index + 1,
                districtId = stop.districtId,
                type = stopTypes[stop.id] ?: StopType.Festival,
                location = GeoPoint(stop.latitude, stop.longitude),
            )
        },
        stamps = earnedStampStops().map { DetailStamp(it.id, it.name, districtName(it.districtId)) },
    )
}

/** Backs the read-only Past and Archived pages. Only an Itinerary in [expectedPhase] is shown. */
class ItineraryDetailViewModel(
    private val itineraryId: String,
    private val expectedPhase: ItineraryPhase,
    private val uid: String?,
    private val itineraries: ItineraryRepository,
    private val events: EventRepository,
    private val zone: ZoneId,
    private val loadDistrictNames: suspend () -> Map<String, String>,
) : ViewModel() {
    private val _state = MutableStateFlow(ItineraryDetailUiState())
    val state: StateFlow<ItineraryDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(detail = LoadState.Loading) }
        viewModelScope.launch {
            val result = try {
                val itinerary = uid?.let { itineraries.getItinerary(it, itineraryId) }
                    ?.takeIf { it.phase == expectedPhase }
                LoadState.Ready(
                    itinerary?.toDetail(events.stopTypesFor(itinerary.stops), loadDistrictNames(), zone),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                LoadState.Failed
            }
            _state.update { it.copy(detail = result) }
        }
    }

    fun requestDelete() = _state.update { it.copy(confirmingDelete = true, deleteFailed = false) }

    fun dismissDelete() = _state.update { it.copy(confirmingDelete = false) }

    fun confirmDelete() {
        val owner = uid ?: return
        _state.update { it.copy(confirmingDelete = false) }
        viewModelScope.launch {
            try {
                // Read again: the phase may have changed since this page opened.
                val current = itineraries.getItinerary(owner, itineraryId)
                if (current?.canDelete == true) {
                    itineraries.deleteItinerary(owner, itineraryId)
                    _state.update { it.copy(deleted = true) }
                } else {
                    _state.update { it.copy(deleteFailed = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(deleteFailed = true) }
            }
        }
    }

    companion object {
        fun factory(
            itineraryId: String,
            expectedPhase: ItineraryPhase,
            uid: String?,
            itineraries: ItineraryRepository,
            events: EventRepository,
            zone: ZoneId,
            loadDistrictNames: suspend () -> Map<String, String>,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ItineraryDetailViewModel(itineraryId, expectedPhase, uid, itineraries, events, zone, loadDistrictNames)
            }
        }
    }
}
