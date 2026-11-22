package com.example.hackapp.itinerary

import com.example.hackapp.data.model.Place
import com.example.hackapp.services.LegEstimator
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * What an edit of an earlier Planning step does to saved Stops. Nothing is saved until the person
 * confirms [proposed] when [needsConfirmation] is true.
 */
data class PlanningChangePreview(
    val previous: Itinerary,
    /** The Itinerary to save: the edit applied, with the Stops that still fit. */
    val proposed: Itinerary,
    val kept: List<Stop>,
    val removed: List<RemovedStop>,
) {
    val needsConfirmation: Boolean get() = removed.isNotEmpty()

    companion object {
        /** An edit that leaves every Stop as it is. */
        fun unchanged(previous: Itinerary, updated: Itinerary) =
            PlanningChangePreview(previous, updated, updated.stops, emptyList())
    }
}

/**
 * Works out which Stops of [previous] still fit [updated], the Itinerary about to be saved after a
 * Date and time, Budget or District edit. The result is state based, so running it again on its own
 * output changes nothing. [places] holds the Places of Meal Stops by id, for hours and budget checks;
 * a Meal Stop without a Place is kept.
 */
fun previewPlanningChange(
    previous: Itinerary,
    updated: Itinerary,
    estimator: LegEstimator,
    zone: ZoneId,
    places: Map<Long, Place> = emptyMap(),
    isOpenAt: (Place, ZonedDateTime) -> Boolean = { place, time -> place.openingHours?.isOpenAt(time) ?: true },
): PlanningChangePreview {
    if (previous.stops.isEmpty()) return PlanningChangePreview.unchanged(previous, updated)

    fun placeOf(stop: Stop): Place? = stop.sourceId.toLongOrNull()?.let { places[it] }
    val isMealOpen = { stop: Stop, time: ZonedDateTime -> placeOf(stop)?.let { isOpenAt(it, time) } ?: true }

    // The Meal Blocks come from the edit, so a block that no longer fits removes its Stop.
    var current = previous.copy(mealBlocks = updated.mealBlocks)
    val removed = mutableListOf<RemovedStop>()
    fun apply(edit: DraftEdit) {
        val step = previewDraftEdit(current, edit, estimator, zone, isMealOpen)
        removed += step.removed
        current = step.proposed
    }
    if (updated.date != previous.date) apply(DraftEdit.ChangeDate(updated.date))
    apply(DraftEdit.ChangeWindow(updated.startTime, updated.endTime, updated.outingKind))
    if (updated.districtIds.isNotEmpty()) apply(DraftEdit.ChangeDistricts(updated.districtIds))

    val kept = current.stops.filter { stop ->
        val over = stop.kind == StopKind.Meal && mealOverBudget(stop, previous, updated, ::placeOf)
        if (over) removed += RemovedStop(stop, StopRemovalReason.MealOverBudget)
        !over
    }
    val proposed = updated.copy(
        stops = kept,
        step = if (removed.isEmpty()) updated.step else minOf(updated.step, PlanningStep.Stops),
    )
    return PlanningChangePreview(previous, proposed, kept, removed.toList())
}

/** True only when this Meal's bracket changed and the Place's price no longer fits the new one. */
private fun mealOverBudget(stop: Stop, previous: Itinerary, updated: Itinerary, placeOf: (Stop) -> Place?): Boolean {
    val block = updated.mealBlocks.firstOrNull { it.id == stop.mealBlockId } ?: return false
    val before = previous.mealBlocks.firstOrNull { it.id == block.id }
    val oldBracket = before?.let { it.budget ?: previous.mealBudgets?.forKind(it.kind) }
    val newBracket = block.budget ?: updated.mealBudgets?.forKind(block.kind)
    if (oldBracket == newBracket) return false
    val place = placeOf(stop) ?: return false
    return !fitsMealBudget(place, newBracket)
}
