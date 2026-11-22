package com.example.hackapp.itinerary

import com.example.hackapp.data.CostStatus
import com.example.hackapp.data.Event
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.Interest
import com.example.hackapp.services.LegEstimate
import com.example.hackapp.services.LegEstimator
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** The space between two consecutive Stops being filled. Null means the edge of the window. */
data class TimeGap(val previous: Stop?, val next: Stop?)

/** The gap after the last active Stop. */
fun Itinerary.currentGap(): TimeGap = TimeGap(activeStops.lastOrNull(), null)

/** The gap following [stopId], or before the first Stop when it is null. */
fun Itinerary.gapAfter(stopId: String?): TimeGap {
    val active = activeStops
    if (stopId == null) return TimeGap(null, active.firstOrNull())
    val index = active.indexOfFirst { it.id == stopId }
    if (index < 0) return currentGap()
    return TimeGap(active[index], active.getOrNull(index + 1))
}

/** Order matters: it is the ranking order after matching Interests. */
enum class PriceRank { KnownWithinBudget, Unknown, KnownOverBudget }

data class EventSearch(
    val itinerary: Itinerary,
    val events: List<Event>,
    val context: PlanningContext,
    val districtId: String,
    val gap: TimeGap = itinerary.currentGap(),
    /** Where the first Leg starts, when known. Later Legs start at the previous Stop. */
    val origin: LatLng? = null,
    /** Shared median of user-reported prices per Event key. */
    val medianPrices: Map<ShowtimeKey, Double> = emptyMap(),
    /** Showtimes must start before this instant. Selection sets it to the next Meal Block start. */
    val until: Instant? = null,
)

data class ReachableShowtime(
    val event: Event,
    /** The Event's own published start, for display. */
    val start: ZonedDateTime,
    val suggestedDeparture: DepartureSuggestion,
    /** Null when the Leg's origin is unknown. */
    val leg: LegEstimate?,
    /**
     * When the person starts this Event: [start], or for a drop-in already in progress the earliest
     * reachable arrival. It becomes the Stop's `start`.
     */
    val arrivalAt: ZonedDateTime = start,
)

/** One title at one venue on one day, with its selectable times in order. */
data class EventCard(
    val key: ShowtimeKey,
    val title: String,
    val venueName: String?,
    val type: String,
    val showtimes: List<ReachableShowtime>,
    val matchesInterest: Boolean,
    val priceRank: PriceRank,
    /** Null when unknown. Free is 0. */
    val priceCad: Double?,
    val travelMinutes: Int?,
    /** A long Event with a published end that may be joined at any time, see [isDropIn]. */
    val isDropIn: Boolean = false,
    /** [priceCad] is the shared median of user reports. */
    val priceIsMedian: Boolean = false,
) {
    val firstStart: ZonedDateTime get() = showtimes.first().arrivalAt
}

data class EventTypeGroup(val type: String, val cards: List<EventCard>)

data class EventHourGroup(val hourStart: ZonedDateTime, val types: List<EventTypeGroup>)

internal const val OTHER_EVENT_TYPE = "other"

internal fun interestOf(eventType: String): Interest? =
    Interest.entries.firstOrNull { it.name.equals(eventType, ignoreCase = true) }

/** Free is $0, then the person's report, the shared median and any published price. */
internal fun knownEventPrice(event: Event?, ownReport: Double?, median: Double?): Double? = when {
    event?.cost?.status == CostStatus.FREE -> 0.0
    ownReport != null -> ownReport
    median != null -> median
    else -> event?.cost?.let { it.maxPrice ?: it.minPrice }
}

internal fun priceRank(priceCad: Double?, budget: BudgetBracket?): PriceRank {
    if (priceCad == null) return PriceRank.Unknown
    val limit = budget?.maxPrice ?: return PriceRank.KnownWithinBudget
    return if (priceCad < limit) PriceRank.KnownWithinBudget else PriceRank.KnownOverBudget
}

/** Reachable showtimes for the gap, in start order. */
fun reachableShowtimes(search: EventSearch, estimator: LegEstimator): List<ReachableShowtime> {
    val itinerary = search.itinerary
    val context = search.context
    val zone = context.zone
    val window = itinerary.planningWindow(zone)
    val blocks = itinerary.activeMealBlocks.map { it.interval(itinerary, zone) }
    val plannedIds = itinerary.stops.filter { it.kind == StopKind.Event }.map { it.sourceId }.toSet()
    val plannedKeys = search.events.filter { it.id in plannedIds }.map { it.showtimeKey() }.toSet()
    val legOrigin = search.gap.previous?.latLng ?: search.origin

    return search.events.asSequence()
        .filter { it.districtId == search.districtId }
        .filter { it.id !in plannedIds && it.showtimeKey() !in plannedKeys }
        .filter { isEligible(it, context) }
        .mapNotNull { event ->
            val start = event.startTime()?.atZoneSameInstant(zone) ?: return@mapNotNull null
            val dropIn = event.isDropIn()
            val target = event.latLng() ?: return@mapNotNull null
            val leg = legOrigin?.let {
                estimator.defaultEstimate(search.gap.previous?.districtId, event.districtId, it, target)
            }

            // Earliest moment the person can be there: the window, now, and the Leg from the previous Stop.
            val previous = search.gap.previous
            var floor = maxOf(window.start, context.now)
            if (previous != null && leg != null) {
                floor = maxOf(floor, previous.plannedDeparture.toInstant().plusSeconds(leg.totalMinutes * 60L))
            }
            // A fixed showtime must start at or after that moment. A drop-in already in progress is joined on arrival.
            if (!dropIn && (start.toInstant() !in window || start.toInstant().isBefore(floor))) return@mapNotNull null
            val arrival = if (dropIn) maxOf(start.toInstant(), floor) else start.toInstant()
            if (arrival !in window) return@mapNotNull null
            if (search.until != null && !arrival.isBefore(search.until)) return@mapNotNull null

            var departure = suggestDeparture(event, itinerary, zone, arrival.atZone(zone)) ?: return@mapNotNull null
            var leaves = departure.plannedDeparture.toInstant()
            if (blocks.any { it.overlaps(arrival, leaves) }) {
                // A drop-in Event stays on the list, but its stay stops when the first Meal Block it crosses starts.
                if (!dropIn) return@mapNotNull null
                val stopsAt = blocks.filter { it.overlaps(arrival, leaves) }.minOf { it.start }
                if (!stopsAt.isAfter(arrival)) return@mapNotNull null
                departure = departure.copy(plannedDeparture = stopsAt.atZone(zone))
                leaves = stopsAt
            }
            val next = search.gap.next
            if (next != null) {
                val mustLeave = next.start.toInstant().minusSeconds(estimator.defaultEstimate(event.districtId, next.districtId, target, next.latLng).totalMinutes * 60L)
                if (leaves.isAfter(mustLeave)) {
                    // A drop-in can be left early for the next Stop. A fixed showtime cannot.
                    if (!dropIn) return@mapNotNull null
                    departure = departure.copy(plannedDeparture = mustLeave.atZone(zone))
                    leaves = mustLeave
                }
            }
            // Every drop-in needs a meaningful stay once all the limits apply.
            if (dropIn && Duration.between(arrival, leaves).toMinutes() < MIN_DROP_IN_STAY_MINUTES) {
                return@mapNotNull null
            }
            ReachableShowtime(event, start, departure, leg, arrivalAt = arrival.atZone(zone))
        }
        .sortedBy { it.arrivalAt.toInstant() }
        .toList()
}

/** Merges showtimes into cards and ranks them. */
fun rankedEventCards(search: EventSearch, estimator: LegEstimator): List<EventCard> {
    val budget = search.itinerary.eventBudget ?: search.context.defaultEventBudget
    return reachableShowtimes(search, estimator)
        .groupBy { it.event.showtimeKey() }
        .map { (key, showtimes) ->
            val first = showtimes.first().event
            val median = search.medianPrices[key]
            val price = knownEventPrice(first, ownReport = null, median = median)
            EventCard(
                key = key,
                title = first.title,
                venueName = first.location.venueName,
                type = first.type,
                showtimes = showtimes,
                isDropIn = first.isDropIn(),
                matchesInterest = interestOf(first.type) in search.context.interests,
                priceRank = priceRank(price, budget),
                priceCad = price,
                priceIsMedian = median != null && first.cost.status != CostStatus.FREE,
                travelMinutes = showtimes.first().leg?.totalMinutes,
            )
        }
        .sortedWith(EventCardRanking)
}

internal val EventCardRanking: Comparator<EventCard> = compareBy<EventCard> { !it.matchesInterest }
    .thenBy { it.priceRank.ordinal }
    .thenBy { it.travelMinutes ?: Int.MAX_VALUE }
    .thenBy { it.firstStart.toInstant() }
    .thenBy { it.title }

/**
 * Groups by 1-hour start block, then type. A card sits in the hour of its first showtime. Types
 * follow their best-ranked card, with "other" last.
 */
fun groupEventCards(cards: List<EventCard>): List<EventHourGroup> =
    cards.groupBy { it.firstStart.truncatedTo(ChronoUnit.HOURS) }
        .toSortedMap(compareBy<ZonedDateTime> { it.toInstant() })
        .map { (hour, hourCards) ->
            val types = hourCards.groupBy { it.type }
                .map { (type, typeCards) -> EventTypeGroup(type, typeCards) }
                .sortedBy { it.type.equals(OTHER_EVENT_TYPE, ignoreCase = true) }
            EventHourGroup(hour, types)
        }

private fun Event.latLng(): LatLng? {
    val latitude = location.latitude ?: return null
    val longitude = location.longitude ?: return null
    return LatLng(latitude, longitude)
}
