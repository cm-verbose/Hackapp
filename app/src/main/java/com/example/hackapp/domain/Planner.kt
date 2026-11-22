package com.example.hackapp.domain

import com.example.hackapp.data.Event
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.data.model.MealSlot
import com.example.hackapp.data.model.Place
import com.example.hackapp.data.model.VenueType
import com.example.hackapp.data.repository.EventQuery
import com.example.hackapp.data.repository.EventRepository
import com.example.hackapp.data.repository.PlaceQuery
import com.example.hackapp.data.repository.PlaceRepository
import com.example.hackapp.services.LegEstimator
import com.example.hackapp.services.LegMode
import com.example.hackapp.ui.theme.StopType
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Everything the person decided so far for the Itinerary they are planning. */
data class PlanInput(
    val window: PlanWindow,
    val audience: Audience,
    val budgetMax: Int?,
    val profile: UserProfile,
)

/** Events of one time block (one hour of start times), grouped by type for display. */
data class TimeBlock(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val groups: List<Pair<StopType, List<Event>>>,
)

/** What StopSelection shows after the stops chosen so far. */
data class SelectionView(
    val blocks: List<TimeBlock>,
    /** Set when the Meal Block starts at or before the next block and no Meal is chosen yet. */
    val mealPrompt: MealBlock?,
)

/**
 * The rules of planning, kept free of Android types so they can be unit-tested.
 * Depends only on the repositories and the Leg estimator.
 */
class ItineraryPlanner(
    private val events: EventRepository,
    private val places: () -> PlaceRepository,
    private val legs: LegEstimator,
    private val zone: ZoneId,
    /** "Now" for the already-started rule; injected so tests are deterministic. */
    private val clock: Clock,
) {
    private fun now(): LocalDateTime = LocalDateTime.ofInstant(clock.instant(), zone)

    private fun EventQuery.forWindow(input: PlanInput, districts: Set<String>?) = copy(
        districtIds = districts,
        startsAtOrAfter = input.window.startAt.atZone(zone).toOffsetDateTime(),
        startsBefore = input.window.endAt.atZone(zone).toOffsetDateTime(),
    )

    /** Events that may be offered: eligible for the person and not started yet. */
    private suspend fun eligible(input: PlanInput, districts: Set<String>): List<Event> {
        val now = now()
        return events.search(EventQuery().forWindow(input, districts))
            .filter { it.isEligible(input.profile, input.audience) }
            .filter { e -> e.startLocal(zone)?.let { !it.isBefore(now) } == true }
    }

    /** Badge numbers for DistrictPick: matching Events per District for the whole window. */
    suspend fun countsByDistrict(input: PlanInput, allDistricts: Collection<String>): Map<String, Int> =
        eligible(input, allDistricts.toSet()).groupingBy { it.districtId!! }.eachCount()

    /** Number of eligible Events of each type in a District (District preview tags). */
    suspend fun typesInDistrict(input: PlanInput, district: String): Map<StopType, Int> =
        eligible(input, setOf(district)).mapNotNull { eventStopType(it.type) }.groupingBy { it }.eachCount()

    /**
     * Events the person can still reach after [previous] (null = first Stop), in [district].
     * Reachable = starts at or after the previous departure plus the Leg estimate.
     * Events inside an unchosen Meal Block are held back until the Meal is chosen.
     */
    suspend fun selection(
        input: PlanInput,
        district: String,
        previous: Stop?,
        meal: MealBlock?,
        mealChosen: Boolean,
        chosenIds: Set<String>,
    ): SelectionView {
        val earliest = previous?.departure ?: input.window.startAt
        val list = eligible(input, setOf(district))
            .filter { it.id !in chosenIds }
            .mapNotNull { e -> e.startLocal(zone)?.let { e to it } }
            .filter { (e, start) ->
                val reach = reachable(previous, e, start, earliest)
                val inMealBlock = meal != null && !mealChosen && start >= meal.blockStart && start < meal.blockEnd
                reach && !inMealBlock
            }
            .sortedWith(compareBy({ it.second }, { -it.first.rank(input.profile, input.budgetMax) }))
        val blocks = list.groupBy { (_, start) -> start.withMinute(0).withSecond(0).withNano(0) }
            .map { (hour, items) ->
                TimeBlock(
                    hour,
                    hour.plusHours(1),
                    items.map { it.first }.groupBy { eventStopType(it.type)!! }.toList(),
                )
            }
            .sortedBy { it.start }
        // The prompt stays in the list, at its time, until a Meal is chosen.
        val prompt = meal?.takeIf { !mealChosen }
        return SelectionView(blocks, prompt)
    }

    private fun event2LegMinutes(from: LatLng?, to: LatLng?): Int =
        if (from != null && to != null) legs.estimate(from, to, LegMode.TRANSIT).totalMinutes else 0

    private fun reachable(previous: Stop?, event: Event, start: LocalDateTime, earliest: LocalDateTime): Boolean {
        if (previous == null) return !start.isBefore(earliest)
        val to = event.latLng()
        val leg = event2LegMinutes(previous.location, to).takeIf { it > 0 }?.toLong()
        val needed = leg ?: (if (previous.districtId == event.districtId) 0L else 15L)
        return !start.isBefore(previous.departure.plusMinutes(needed))
    }

    /** Converts a chosen Event to a Stop; [departureOverride] is used when the Event has no end time. */
    fun eventStop(event: Event, departureOverride: LocalTime?): Stop? {
        val start = event.startLocal(zone) ?: return null
        val departure = event.endLocal(zone)
            ?: departureOverride?.let { t -> start.toLocalDate().atTime(t).let { if (it.isAfter(start)) it else it.plusDays(1) } }
            ?: return null
        return Stop(
            id = event.id,
            type = eventStopType(event.type) ?: return null,
            title = event.title,
            details = event.location.venueName ?: event.location.address,
            districtId = event.districtId.orEmpty(),
            lat = event.location.latitude,
            lng = event.location.longitude,
            start = start,
            departure = departure,
            price = event.stopPrice(),
        )
    }

    /** Meals in or near [district], by closeness to the last Stop. Bars only in the evening and for adult audiences. */
    suspend fun mealOptions(
        input: PlanInput,
        district: String,
        anchor: LatLng?,
        block: MealBlock,
        profileCuisines: Set<String>,
    ): List<Place> {
        val repo = places()
        val center = anchor ?: districtCenter(repo, district) ?: return emptyList()
        var types = repo.venueTypesFor(block.slot)
        val barsOk = input.audience == Audience.ADULTS_ONLY && block.mealStart.toLocalTime() >= BAR_FROM
        if (!barsOk) types = types - VenueType.BAR
        val zoned = block.mealStart.atZone(zone)
        val found = repo.search(
            PlaceQuery(
                center = center,
                radiusMeters = 1500.0,
                venueTypes = types,
                maxPrice = input.budgetMax,
                openAt = zoned,
                limit = 80,
            ),
        ).filter { it.borough == district || anchor != null }
        // Interests rank cuisines up; they never hide places.
        val groups = repo.cuisineGroups().filter { it.code in profileCuisines }.flatMap { it.cuisines }.toSet()
        return found
            .sortedWith(compareBy({ if (it.cuisines.any { c -> c in groups }) 0 else 1 }, { it.distanceMeters ?: 0.0 }))
            .take(30)
    }

    private suspend fun districtCenter(repo: PlaceRepository, district: String): LatLng? {
        val inside = repo.allPlaces().filter { it.borough == district }
        if (inside.isEmpty()) return null
        return LatLng(inside.map { it.location.lat }.average(), inside.map { it.location.lng }.average())
    }

    fun mealStop(place: Place, block: MealBlock, district: String): Stop = Stop(
        id = "place:${place.id}",
        type = if (place.venueType == VenueType.BAR) StopType.Bar else StopType.Meal,
        title = place.name,
        details = place.address,
        districtId = place.borough ?: district,
        lat = place.location.lat,
        lng = place.location.lng,
        start = block.mealStart,
        departure = block.mealStart.plusMinutes(MealBlock.MEAL_MINUTES),
        price = StopPrice(text = place.price?.label()),
    )

    companion object {
        val BAR_FROM: LocalTime = LocalTime.of(17, 0)

        /** Standard meal times offered by MealChoice; with [now], meals that already started are dropped. */
        fun mealTimes(window: PlanWindow, now: LocalDateTime? = null): List<MealBlock> =
            listOf(
                MealSlot.LUNCH to LocalTime.of(12, 0),
                MealSlot.DINNER to LocalTime.of(18, 30),
                MealSlot.EVENING to LocalTime.of(22, 0),
            ).mapNotNull { (slot, time) ->
                val at = listOf(window.date.atTime(time), window.date.plusDays(1).atTime(time))
                    .firstOrNull {
                        it >= window.startAt && it.plusMinutes(MealBlock.MEAL_MINUTES) <= window.endAt &&
                            (now == null || !it.isBefore(now))
                    }
                at?.let { MealBlock(slot, it) }
            }
    }
}
