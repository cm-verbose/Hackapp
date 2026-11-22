package com.example.hackapp.itinerary

import java.time.ZoneId

/**
 * What a Meal choice change does to saved Stops, so the page can warn before saving. A Stop tied to
 * a Meal Block is removed when that block is gone or its start moved, because the Stop's time no
 * longer matches. An Event Stop is removed when its stay overlaps an active block. Losing a Stop
 * returns the draft to Stop selection.
 */
fun previewMealChange(previous: Itinerary, updated: Itinerary, zone: ZoneId): PlanningChangePreview {
    val before = previous.mealBlocks.associateBy { it.id }
    val after = updated.mealBlocks.associateBy { it.id }
    val reserved = updated.activeMealBlocks.map { it.interval(updated, zone) }
    val removed = updated.stops.mapNotNull { stop ->
        val blockId = stop.mealBlockId
        val reason = when {
            blockId != null ->
                StopRemovalReason.MealBlockRemoved.takeIf { after[blockId]?.start.let { it == null || it != before[blockId]?.start } }
            stop.kind == StopKind.Event &&
                reserved.any { it.overlaps(stop.start.toInstant(), stop.plannedDeparture.toInstant()) } ->
                StopRemovalReason.NoLongerReachable
            else -> null
        }
        reason?.let { RemovedStop(stop, it) }
    }
    if (removed.isEmpty()) return PlanningChangePreview.unchanged(previous, updated)
    val gone = removed.map { it.stop.id }.toSet()
    val kept = updated.stops.filterNot { it.id in gone }
    val proposed = updated.copy(stops = kept, step = minOf(updated.step, PlanningStep.Stops))
    return PlanningChangePreview(previous, proposed, kept, removed)
}

/** The Itinerary to save once the person has accepted [previewMealChange]. */
fun reconcileMealStops(previous: Itinerary, updated: Itinerary, zone: ZoneId): Itinerary =
    previewMealChange(previous, updated, zone).proposed
