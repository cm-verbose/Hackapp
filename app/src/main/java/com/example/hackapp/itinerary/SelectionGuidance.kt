package com.example.hackapp.itinerary

import com.example.hackapp.data.Event
import com.example.hackapp.services.LegEstimator
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/*
 * Nudges shown during Event and Meal selection, so a person fills their day instead of staying in
 * one District or losing their afternoon to a Meal. Each one is a suggestion: nothing changes
 * until the person accepts it.
 */

/** The first District's first Event starts at least this long after the gap opens. */
const val LATE_START_MINUTES = 120L

/** Reordering must bring the first Event at least this much earlier to be worth offering. */
const val REORDER_GAIN_MINUTES = 60L

/** A Meal that hides at least this many Events, and at least half of them, is worth moving. */
const val MEAL_CONFLICT_MIN_LOST = 3

/** What selection asks before moving on: another Event here, or the Meal that is due. */
enum class ContinueKind { Event, Meal }

/**
 * After each new Stop, selection asks whether to add another one in [districtId] before moving on
 * to the next District. Null when there is no next District or no Event planned here yet. The
 * question is about the Meal when the next Meal Block is due, otherwise about another Event.
 */
fun Itinerary.continuePromptKind(districtId: String, mealPromptReached: Boolean): ContinueKind? {
    if (districtAfter(districtId) == null) return null
    if (activeStops.none { it.districtId == districtId && it.kind == StopKind.Event }) return null
    return if (mealPromptReached) ContinueKind.Meal else ContinueKind.Event
}

/**
 * True when nothing else fits the rest of the day: no Event is reachable in any District still
 * ahead, and no Meal Block is waiting for an answer.
 */
fun Itinerary.isDayComplete(
    events: List<Event>,
    context: PlanningContext,
    districtId: String,
    estimator: LegEstimator,
): Boolean {
    if (mealBlockToAnswer(context.now, context.zone) != null) return false
    return remainingEventCount(this, events, context, districtsAhead(districtId), estimator) == 0
}

/** The earliest reachable Event in each District, from the start of the window, or null when none. */
fun earliestEventStarts(
    itinerary: Itinerary,
    events: List<Event>,
    context: PlanningContext,
    estimator: LegEstimator,
): Map<String, Instant?> = itinerary.districtIds.associateWith { district ->
    val search = EventSearch(itinerary, events, context, district, gap = TimeGap(null, null))
    reachableShowtimes(search, estimator).minOfOrNull { it.arrivalAt.toInstant() }
}

/**
 * The District order that follows the day, offered when the first District's Events start late.
 * Null when nothing should be offered: Stops are already planned, there is one District, the first
 * District starts soon enough, or no order would start meaningfully earlier.
 *
 * Districts are sorted by their first Event, and Districts with none go last in their old order.
 */
fun Itinerary.chronologicalDistrictOrder(starts: Map<String, Instant?>, gapStart: Instant): List<String>? {
    if (activeStops.isNotEmpty() || districtIds.size < 2) return null
    val firstStart = starts[districtIds.first()]
    val late = firstStart == null || Duration.between(gapStart, firstStart).toMinutes() >= LATE_START_MINUTES
    if (!late) return null
    val order = districtIds.sortedWith(compareBy { starts[it] ?: Instant.MAX })
    if (order == districtIds) return null
    val newStart = starts[order.first()] ?: return null
    val gain = if (firstStart == null) Long.MAX_VALUE else Duration.between(newStart, firstStart).toMinutes()
    return order.takeIf { gain >= REORDER_GAIN_MINUTES }
}

/** How many Event cards are left for the rest of the day in the Districts still ahead, with and without one Meal Block. */
data class MealImpact(val blockId: String, val withMeal: Int, val withoutMeal: Int) {
    val lost: Int get() = withoutMeal - withMeal

    /** True when the Meal removes every Event, or at least [MEAL_CONFLICT_MIN_LOST] and half of them. */
    val isConflict: Boolean
        get() = lost > 0 && (withMeal == 0 || (lost >= MEAL_CONFLICT_MIN_LOST && lost * 2 >= withoutMeal))
}

/** The current District and the ones after it, in visit order. */
fun Itinerary.districtsAhead(districtId: String): List<String> =
    districtIds.indexOf(districtId).let { if (it < 0) listOf(districtId) else districtIds.drop(it) }

/** Counts the Event cards left after the current gap, for the whole rest of the day, in [districtIds]. */
fun remainingEventCount(
    itinerary: Itinerary,
    events: List<Event>,
    context: PlanningContext,
    districtIds: List<String>,
    estimator: LegEstimator,
): Int = districtIds.sumOf { district ->
    val search = EventSearch(itinerary, events, context, district)
    reachableShowtimes(search, estimator).map { it.event.showtimeKey() }.distinct().size
}

/** What [blockId] costs in Events for the rest of the day in [districtIds]. */
fun mealImpact(
    itinerary: Itinerary,
    blockId: String,
    events: List<Event>,
    context: PlanningContext,
    districtIds: List<String>,
    estimator: LegEstimator,
): MealImpact {
    val withMeal = remainingEventCount(itinerary, events, context, districtIds, estimator)
    val freed = itinerary.copy(mealBlocks = itinerary.mealBlocks.map { if (it.id == blockId) it.copy(skipped = true) else it })
    val withoutMeal = remainingEventCount(freed, events, context, districtIds, estimator)
    return MealImpact(blockId, withMeal, withoutMeal)
}

/**
 * Moves one Meal Block to [start]. Null when the block would leave the window, overlap another
 * active block or overlap a planned Stop, so the person can only preview times that are valid.
 */
fun Itinerary.withMealStart(blockId: String, start: LocalTime, zone: ZoneId): Itinerary? {
    val block = mealBlocks.firstOrNull { it.id == blockId } ?: return null
    val moved = copy(mealBlocks = mealBlocks.map { if (it.id == blockId) it.copy(start = start) else it })
    val span = block.copy(start = start).interval(moved, zone)
    if (!moved.planningWindow(zone).encloses(span)) return null
    val overlaps = moved.activeMealBlocks
        .filter { it.id != blockId }
        .any { span.overlaps(it.interval(moved, zone).start, it.interval(moved, zone).end) }
    val hitsStop = moved.activeStops
        .filter { it.mealBlockId != blockId }
        .any { span.overlaps(it.start.toInstant(), it.plannedDeparture.toInstant()) }
    return moved.takeUnless { overlaps || hitsStop }
}
