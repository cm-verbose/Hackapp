package com.example.hackapp.itinerary

import com.example.hackapp.data.Event
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.data.model.Place
import com.example.hackapp.services.LegEstimate
import com.example.hackapp.services.LegEstimator
import com.example.hackapp.services.LegMode
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

private const val DEFAULT_STAY_MINUTES = 90

private val TypicalStayMinutes = mapOf(
    "cinema" to 120,
    "comedy" to 90,
    "exhibition" to 60,
    "family" to 60,
    "festival" to 120,
    "music" to 120,
    "other" to DEFAULT_STAY_MINUTES,
    "sport" to 120,
    "theatre" to 150,
)

/** Typical stay for an Event type, used to prefill Planned Departure. */
fun typicalStayMinutes(eventType: String): Int =
    TypicalStayMinutes[eventType.lowercase(Locale.ROOT)] ?: DEFAULT_STAY_MINUTES

/** A prefilled Planned Departure. The source end time is never invented. */
data class DepartureSuggestion(val plannedDeparture: ZonedDateTime, val hasPublishedEnd: Boolean)

/** The published end, or the typical stay when there is none, kept inside the time window. */
fun suggestDeparture(event: Event, itinerary: Itinerary, zone: ZoneId): DepartureSuggestion? {
    val start = event.startTime()?.atZoneSameInstant(zone) ?: return null
    val published = event.publishedEnd()?.atZoneSameInstant(zone)
    val departure = published ?: start.plusMinutes(typicalStayMinutes(event.type).toLong())
    val windowEnd = itinerary.windowEnd(zone)
    return DepartureSuggestion(
        plannedDeparture = if (departure.isAfter(windowEnd)) windowEnd else departure,
        hasPublishedEnd = published != null,
    )
}

internal val Stop.latLng: LatLng get() = LatLng(latitude, longitude)

internal fun LegEstimator.transit(from: LatLng, to: LatLng): LegEstimate =
    estimate(from, to, LegMode.TRANSIT)

/** The earlier of the Planned Departure and the time needed to reach [next] by transit. */
fun leaveBy(stop: Stop, next: Stop?, estimator: LegEstimator): OffsetDateTime {
    if (next == null) return stop.plannedDeparture
    val latest = next.start.minusMinutes(estimator.transit(stop.latLng, next.latLng).totalMinutes.toLong())
    return if (latest.isBefore(stop.plannedDeparture)) latest else stop.plannedDeparture
}

/** Leave By for a Stop in this Itinerary, ignoring skipped Stops. Null if it is not active. */
fun leaveBy(itinerary: Itinerary, stopId: String, estimator: LegEstimator): OffsetDateTime? {
    val active = itinerary.activeStops
    val index = active.indexOfFirst { it.id == stopId }
    if (index < 0) return null
    return leaveBy(active[index], active.getOrNull(index + 1), estimator)
}

/** Builds an Event Stop. [plannedDeparture] comes from the Planned Departure sheet. */
fun eventStop(event: Event, plannedDeparture: ZonedDateTime, ticketPriceCad: Double? = null): Stop? {
    val start = event.startTime() ?: return null
    val latitude = event.location.latitude ?: return null
    val longitude = event.location.longitude ?: return null
    val districtId = event.districtId ?: return null
    return Stop(
        id = "event-${event.id}",
        kind = StopKind.Event,
        sourceId = event.id,
        name = event.title,
        districtId = districtId,
        latitude = latitude,
        longitude = longitude,
        start = start,
        plannedDeparture = plannedDeparture.toOffsetDateTime(),
        hasPublishedEnd = event.publishedEnd() != null,
        ticketPriceCad = ticketPriceCad,
    )
}

/** Builds a Meal Stop filling [block]. Null when the Place has no District. */
fun mealStop(place: Place, block: MealBlock, itinerary: Itinerary, zone: ZoneId): Stop? {
    val districtId = place.borough ?: return null
    val start = itinerary.resolve(block.start, zone)
    return Stop(
        id = "meal-${block.id}-${place.id}",
        kind = StopKind.Meal,
        sourceId = place.id.toString(),
        name = place.name,
        districtId = districtId,
        latitude = place.location.lat,
        longitude = place.location.lng,
        start = start.toOffsetDateTime(),
        plannedDeparture = start.plusMinutes(block.kind.blockMinutes.toLong()).toOffsetDateTime(),
        hasPublishedEnd = false,
        mealBlockId = block.id,
    )
}

/** Adds [stop] in visit order. A Meal Stop replaces any other Stop of the same Meal Block. */
fun Itinerary.withStop(stop: Stop): Itinerary =
    copy(stops = (stops.filterNot { it.id == stop.id || (stop.mealBlockId != null && it.mealBlockId == stop.mealBlockId) } + stop).sortedBy { it.start.toInstant() })

/** Finish rule: an Itinerary needs at least one Event. */
fun canFinish(itinerary: Itinerary): Boolean = itinerary.activeStops.any { it.kind == StopKind.Event }
