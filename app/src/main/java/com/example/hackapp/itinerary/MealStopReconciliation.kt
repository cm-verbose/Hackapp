package com.example.hackapp.itinerary

/**
 * Applies a Meal choice change to saved Stops. A Stop tied to a Meal Block is removed when that block
 * is gone or its start moved, because the Stop's time no longer matches. Other Stops are kept.
 */
fun reconcileMealStops(previous: Itinerary, updated: Itinerary): Itinerary {
    val before = previous.mealBlocks.associateBy { it.id }
    val after = updated.mealBlocks.associateBy { it.id }
    val stops = updated.stops.filter { stop ->
        val blockId = stop.mealBlockId ?: return@filter true
        val block = after[blockId] ?: return@filter false
        before[blockId]?.start == block.start
    }
    return if (stops.size == updated.stops.size) updated else updated.copy(stops = stops)
}
