package com.example.hackapp.itinerary

import java.time.ZoneId

/**
 * Applies a Meal choice change to saved Stops. A Stop tied to a Meal Block is removed when that block
 * is gone or its start moved, because the Stop's time no longer matches. An Event Stop is removed
 * when its stay overlaps an active block. Losing a Stop returns the draft to Stop selection.
 */
fun reconcileMealStops(previous: Itinerary, updated: Itinerary, zone: ZoneId): Itinerary {
    val before = previous.mealBlocks.associateBy { it.id }
    val after = updated.mealBlocks.associateBy { it.id }
    val reserved = updated.activeMealBlocks.map { it.interval(updated, zone) }
    val stops = updated.stops.filter { stop ->
        val blockId = stop.mealBlockId
        if (blockId != null) {
            val block = after[blockId] ?: return@filter false
            return@filter before[blockId]?.start == block.start
        }
        stop.kind != StopKind.Event ||
            reserved.none { it.overlaps(stop.start.toInstant(), stop.plannedDeparture.toInstant()) }
    }
    if (stops.size == updated.stops.size) return updated
    return updated.copy(stops = stops, step = minOf(updated.step, PlanningStep.Stops))
}
