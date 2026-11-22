package com.example.hackapp.scaffold.screens.planning

import com.example.hackapp.data.Event
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.startTime
import com.example.hackapp.itinerary.suggestDeparture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class DepartureError {
    /** The chosen time is after the end of the Itinerary window. */
    OutsideWindow,

    /** The stay is zero or negative. */
    NotAfterStart,
}

data class PlannedDepartureUiState(
    val eventId: String,
    val eventTitle: String,
    val eventType: String,
    val start: ZonedDateTime,
    val windowEnd: ZonedDateTime,
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

/** State holder for the Planned Departure sheet. Null from [create] when the Event has no start. */
class PlannedDepartureHolder private constructor(
    private val zone: ZoneId,
    private val windowEnd: ZonedDateTime,
    private val start: ZonedDateTime,
    private val prefill: ZonedDateTime,
    initial: PlannedDepartureUiState,
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<PlannedDepartureUiState> = _state.asStateFlow()

    /**
     * Sets the departure by clock time. A time at or before the start rolls to the next day, so
     * 01:00 after a 23:00 start means after midnight.
     */
    fun setTime(time: LocalTime) {
        var candidate = start.toLocalDate().atTime(time).atZone(zone)
        if (!candidate.isAfter(start)) candidate = start.toLocalDate().plusDays(1).atTime(time).atZone(zone)
        if (candidate.isAfter(windowEnd)) {
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
        if (candidate.isAfter(windowEnd)) {
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
        fun create(event: Event, itinerary: Itinerary, zone: ZoneId): PlannedDepartureHolder? {
            val start = event.startTime()?.atZoneSameInstant(zone) ?: return null
            val suggestion = suggestDeparture(event, itinerary, zone) ?: return null
            val departure = suggestion.plannedDeparture
            val windowEnd = itinerary.windowEnd(zone)
            val state = PlannedDepartureUiState(
                eventId = event.id,
                eventTitle = event.title,
                eventType = event.type,
                start = start,
                windowEnd = windowEnd,
                departure = departure,
                stayMinutes = Duration.between(start, departure).toMinutes().toInt(),
                hasPublishedEnd = suggestion.hasPublishedEnd,
                isTypicalStay = !suggestion.hasPublishedEnd,
                crossesMidnight = departure.toLocalDate() != start.toLocalDate(),
                error = if (!departure.isAfter(start)) DepartureError.NotAfterStart else null,
            )
            return PlannedDepartureHolder(zone, windowEnd, start, departure, state)
        }
    }
}
