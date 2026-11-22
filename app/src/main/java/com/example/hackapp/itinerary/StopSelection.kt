package com.example.hackapp.itinerary

import com.example.hackapp.data.Event
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A published stay of at least this long makes an Event drop-in. */
const val DROP_IN_MIN_MINUTES = 240L

/** A drop-in Event already running is offered only with at least this much stay left. */
const val MIN_DROP_IN_STAY_MINUTES = 60L

/**
 * A drop-in Event can be joined at any time during its run, such as an exhibition or a festival day.
 * It must have published an end, because without one its stay is only a guess, and it must last at
 * least [DROP_IN_MIN_MINUTES] from start to end.
 */
fun Event.isDropIn(): Boolean {
    val start = startTime() ?: return false
    val end = publishedEnd() ?: return false
    return Duration.between(start, end).toMinutes() >= DROP_IN_MIN_MINUTES
}

/** Where the open time gap starts: the last Stop's Planned Departure, else the later of window start and now. */
fun Itinerary.gapStart(now: Instant, zone: ZoneId): Instant =
    activeStops.lastOrNull()?.plannedDeparture?.toInstant()
        ?: maxOf(windowStart(zone).toInstant(), now)

/**
 * The next Meal Block that still needs an answer: active, with no Meal Stop, and ending after the
 * gap starts. A block that ended before the gap began can no longer be reached.
 */
fun Itinerary.pendingMealBlock(now: Instant, zone: ZoneId): MealBlock? {
    val from = gapStart(now, zone)
    val answered = stops.mapNotNull { it.mealBlockId }.toSet()
    return activeMealBlocks
        .filter { it.id !in answered && it.interval(this, zone).end.isAfter(from) }
        .minByOrNull { it.interval(this, zone).start }
}

/** The District selection is planning in, from the saved one or else where the last Stop is. */
fun Itinerary.selectionDistrict(): String? =
    planningDistrictId?.takeIf { it in districtIds }
        ?: activeStops.lastOrNull()?.districtId?.takeIf { it in districtIds }
        ?: districtIds.firstOrNull()

private fun Itinerary.hasStopIn(districtId: String) = activeStops.any { it.districtId == districtId }

/** Keeps only the Districts that have a Stop. */
fun Itinerary.withoutEmptyDistricts(): Itinerary =
    copy(districtIds = districtIds.filter { hasStopIn(it) })

/** Moves planning to [target], dropping [from] when it has no Stop. */
fun Itinerary.movedToDistrict(from: String, target: String): Itinerary {
    val kept = if (hasStopIn(from)) districtIds else districtIds - from
    return copy(districtIds = kept, planningDistrictId = target)
}

/** The District after [districtId] in visit order, or null on the last one. */
fun Itinerary.districtAfter(districtId: String): String? =
    districtIds.getOrNull(districtIds.indexOf(districtId) + 1).takeIf { districtId in districtIds }

/** The District before [districtId] in visit order, or null on the first one. */
fun Itinerary.districtBefore(districtId: String): String? =
    districtIds.getOrNull(districtIds.indexOf(districtId) - 1).takeIf { districtId in districtIds }

/** Ends selection: drops Districts with no Stop and goes to Review. Null unless an Event is planned. */
fun Itinerary.finishedSelection(): Itinerary? {
    if (!canFinish(this)) return null
    return withoutEmptyDistricts().let { it.copy(step = maxOf(it.step, PlanningStep.Review)) }
}

/** Removes a Meal Stop without skipping its block, so the Meal can be chosen again. */
fun Itinerary.withoutStop(stopId: String): Itinerary = copy(stops = stops.filterNot { it.id == stopId })
