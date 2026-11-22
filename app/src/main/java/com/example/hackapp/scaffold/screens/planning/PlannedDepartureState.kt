package com.example.hackapp.scaffold.screens.planning

import com.example.hackapp.data.Event
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.activeMealBlocks
import com.example.hackapp.itinerary.activeStops
import com.example.hackapp.itinerary.interval
import com.example.hackapp.itinerary.latLng
import com.example.hackapp.itinerary.startTime
import com.example.hackapp.itinerary.suggestDeparture
import com.example.hackapp.itinerary.transit
import com.example.hackapp.itinerary.typicalStayMinutes
import com.example.hackapp.services.LegEstimator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class DepartureError {
    /** The chosen time is after [PlannedDepartureUiState.latestDeparture]. */
    OutsideWindow,

    /** The stay is zero or negative. */
    NotAfterStart,
}

data class PlannedDepartureUiState(
    val eventId: String,
    val eventTitle: String,
    val eventType: String,
    val start: ZonedDateTime,
    /** The latest allowed departure. Show it with an [DepartureError.OutsideWindow] error. */
    val latestDeparture: ZonedDateTime,
    /** The typical stay for the Event type, in minutes. */
    val typicalStayMinutes: Int,
    /** The last valid choice. An invalid pick leaves it unchanged and sets [error]. */
    val departure: ZonedDateTime,
    /** Minutes from [start] to [departure]. */
    val stayMinutes: Int,
    /** The Event published an end time. The sheet then does not need to open. */
    val hasPublishedEnd: Boolean,
    /** [departure] is still the typical stay for the Event type. */
    val isTypicalStay: Boolean,
    /** [departure] is on a later calendar day than [start]. */
    val crossesMidnight: Boolean,
    /** The time the person tried when [error] is set. */
    val rejectedTime: LocalTime? = null,
    val error: DepartureError? = null,
) {
    val canConfirm: Boolean get() = error == null
}

/**
 * The latest the person can leave the Event: the earliest of the window end, the start of the next
 * active Meal Block, and the next active Stop's start minus the transit Leg to it.
 */
fun latestDeparture(event: Event, itinerary: Itinerary, zone: ZoneId, estimator: LegEstimator): ZonedDateTime? {
    val start = event.startTime()?.atZoneSameInstant(zone) ?: return null
    var latest = itinerary.windowEnd(zone).toInstant()
    itinerary.activeMealBlocks
        .map { it.interval(itinerary, zone).start }
        .filter { it.isAfter(start.toInstant()) }
        .minOrNull()
        ?.let { if (it.isBefore(latest)) latest = it }
    val lat = event.location.latitude
    val lng = event.location.longitude
    val next = itinerary.activeStops
        .filter { it.id != "event-${event.id}" && it.start.toInstant().isAfter(start.toInstant()) }
        .minByOrNull { it.start.toInstant() }
    if (next != null) {
        val toNextSeconds = if (lat != null && lng != null) {
            estimator.transit(LatLng(lat, lng), next.latLng).totalMinutes * 60L
        } else {
            0L
        }
        val bound = next.start.toInstant().minusSeconds(toNextSeconds)
        if (bound.isBefore(latest)) latest = bound
    }
    return latest.atZone(zone)
}

/** State holder for the Planned Departure sheet. Null from [create] when the Event has no start. */
class PlannedDepartureHolder private constructor(
    private val zone: ZoneId,
    private val latest: ZonedDateTime,
    private val start: ZonedDateTime,
    private val prefill: ZonedDateTime,
    initial: PlannedDepartureUiState,
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<PlannedDepartureUiState> = _state.asStateFlow()

    /**
     * Sets the departure by clock time. A time at or before the start rolls to the next day, so
     * 01:00 after a 23:00 start means after midnight. A time equal to the one already shown is
     * ignored, so a prefilled time keeps its offset on the night clocks go back.
     */
    fun setTime(time: LocalTime) {
        val current = _state.value
        if (current.error == null && time == current.departure.toLocalTime()) return
        var candidate = start.toLocalDate().atTime(time).atZone(zone)
        if (!candidate.isAfter(start)) candidate = start.toLocalDate().plusDays(1).atTime(time).atZone(zone)
        if (candidate.isAfter(latest)) {
            _state.update { it.copy(error = DepartureError.OutsideWindow, rejectedTime = time) }
        } else {
            apply(candidate)
        }
    }

    /** Sets the stay length, for steppers. */
    fun setStayMinutes(minutes: Int) {
        if (minutes <= 0) {
            _state.update { it.copy(error = DepartureError.NotAfterStart, rejectedTime = null) }
            return
        }
        val candidate = start.plusMinutes(minutes.toLong())
        if (candidate.isAfter(latest)) {
            _state.update { it.copy(error = DepartureError.OutsideWindow, rejectedTime = candidate.toLocalTime()) }
        } else {
            apply(candidate)
        }
    }

    /** Goes back to the typical stay. */
    fun reset() = apply(prefill)

    /** The Planned Departure to pass to `eventStop`, or null while there is an error. */
    fun confirm(): ZonedDateTime? = _state.value.takeIf { it.canConfirm }?.departure

    private fun apply(departure: ZonedDateTime) {
        _state.update {
            it.copy(
                departure = departure,
                stayMinutes = Duration.between(start, departure).toMinutes().toInt(),
                isTypicalStay = !it.hasPublishedEnd && departure == prefill,
                crossesMidnight = departure.toLocalDate() != start.toLocalDate(),
                rejectedTime = null,
                error = null,
            )
        }
    }

    companion object {
        /** [latestDeparture] comes from the top-level `latestDeparture(...)`. */
        fun create(
            event: Event,
            itinerary: Itinerary,
            zone: ZoneId,
            latestDeparture: ZonedDateTime,
        ): PlannedDepartureHolder? {
            val start = event.startTime()?.atZoneSameInstant(zone) ?: return null
            val suggestion = suggestDeparture(event, itinerary, zone) ?: return null
            val suggested = suggestion.plannedDeparture
            val departure = if (suggested.isAfter(latestDeparture)) latestDeparture else suggested
            val state = PlannedDepartureUiState(
                eventId = event.id,
                eventTitle = event.title,
                eventType = event.type,
                start = start,
                latestDeparture = latestDeparture,
                typicalStayMinutes = typicalStayMinutes(event.type),
                departure = departure,
                stayMinutes = Duration.between(start, departure).toMinutes().toInt(),
                hasPublishedEnd = suggestion.hasPublishedEnd,
                isTypicalStay = !suggestion.hasPublishedEnd,
                crossesMidnight = departure.toLocalDate() != start.toLocalDate(),
                error = if (!departure.isAfter(start)) DepartureError.NotAfterStart else null,
            )
            return PlannedDepartureHolder(zone, latestDeparture, start, departure, state)
        }
    }
}
