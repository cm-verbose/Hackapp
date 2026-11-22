package com.example.hackapp.itinerary

import com.example.hackapp.services.LegEstimator
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** A change to an earlier Planning step. */
sealed interface DraftEdit {
    data class ChangeDate(val date: LocalDate) : DraftEdit
    data class ChangeWindow(
        val startTime: LocalTime,
        val endTime: LocalTime,
        val outingKind: OutingKind? = null,
    ) : DraftEdit
    data class ChangeDistricts(val districtIds: List<String>) : DraftEdit
    data class RemoveStop(val stopId: String) : DraftEdit
}

enum class StopRemovalReason {
    RemovedByPerson,
    DateChanged,
    OutsideWindow,
    DistrictRemoved,
    MealBlockRemoved,
    MealClosed,
    NoLongerReachable,

    /** The Meal's price is above the new budget for its Meal Block. */
    MealOverBudget,
}

data class RemovedStop(val stop: Stop, val reason: StopRemovalReason)

/** The outcome of an edit. Nothing is saved until [proposed] is confirmed and stored. */
data class DraftEditPreview(
    val original: Itinerary,
    val proposed: Itinerary,
    val kept: List<Stop>,
    val removed: List<RemovedStop>,
) {
    /** True when Stops other than the one the person chose to remove would be lost. */
    val needsWarning: Boolean get() = removed.any { it.reason != StopRemovalReason.RemovedByPerson }
}

/**
 * Works out which Stops survive [edit], without changing [itinerary]. Event Stops are fixed to
 * their date, so a new date removes them. Meal Stops move to the new date at the same clock time
 * when [isMealOpen] allows it.
 */
fun previewDraftEdit(
    itinerary: Itinerary,
    edit: DraftEdit,
    estimator: LegEstimator,
    zone: ZoneId,
    isMealOpen: (Stop, ZonedDateTime) -> Boolean = { _, _ -> true },
): DraftEditPreview {
    val edited = when (edit) {
        is DraftEdit.ChangeDate -> itinerary.copy(date = edit.date)
        is DraftEdit.ChangeWindow -> itinerary.copy(
            startTime = edit.startTime,
            endTime = edit.endTime,
            outingKind = edit.outingKind ?: itinerary.outingKind,
        )
        is DraftEdit.ChangeDistricts -> itinerary.copy(districtIds = edit.districtIds)
        is DraftEdit.RemoveStop -> itinerary
    }
    val target = edited.copy(mealBlocks = edited.mealBlocks.filter { it.fitsWindow(edited, zone) })
    val window = target.planningWindow(zone)
    val blockIds = target.mealBlocks.map { it.id }.toSet()
    val dateChanged = target.date != itinerary.date

    val kept = mutableListOf<Stop>()
    val removed = mutableListOf<RemovedStop>()

    for (stop in itinerary.stops) {
        val moved = if (dateChanged) stop.movedTo(target, zone) else stop
        val fitted = if (moved.plannedDeparture.toInstant().isAfter(window.end)) {
            moved.copy(plannedDeparture = target.windowEnd(zone).toOffsetDateTime())
        } else {
            moved
        }
        val previous = kept.lastOrNull { it.status != StopStatus.Skipped }
        val reason = when {
            edit is DraftEdit.RemoveStop && stop.id == edit.stopId -> StopRemovalReason.RemovedByPerson
            dateChanged && stop.kind == StopKind.Event -> StopRemovalReason.DateChanged
            edit is DraftEdit.ChangeDistricts && stop.districtId !in edit.districtIds ->
                StopRemovalReason.DistrictRemoved
            stop.mealBlockId != null && stop.mealBlockId !in blockIds -> StopRemovalReason.MealBlockRemoved
            moved.start.toInstant() !in window -> StopRemovalReason.OutsideWindow
            dateChanged && stop.kind == StopKind.Meal && !isMealOpen(stop, moved.start.atZoneSameInstant(zone)) ->
                StopRemovalReason.MealClosed
            previous != null && !canReach(previous, fitted, estimator) -> StopRemovalReason.NoLongerReachable
            else -> null
        }
        if (reason == null) kept += fitted else removed += RemovedStop(stop, reason)
    }
    return DraftEditPreview(itinerary, target.copy(stops = kept.toList()), kept.toList(), removed.toList())
}

private fun canReach(from: Stop, to: Stop, estimator: LegEstimator): Boolean {
    val travel = estimator.transit(from.latLng, to.latLng).totalMinutes * 60L
    return !from.plannedDeparture.toInstant().plusSeconds(travel).isAfter(to.start.toInstant())
}

/** Keeps the clock time and length of a Stop on the edited Itinerary's date. */
private fun Stop.movedTo(target: Itinerary, zone: ZoneId): Stop {
    val length = Duration.between(start.toInstant(), plannedDeparture.toInstant())
    val newStart = target.resolve(start.atZoneSameInstant(zone).toLocalTime(), zone)
    return copy(
        start = newStart.toOffsetDateTime(),
        plannedDeparture = newStart.plus(length).toOffsetDateTime(),
    )
}

/**
 * Drops Meal Blocks that no longer fit this Itinerary's window. When any is dropped, the draft goes
 * back to the Meal choice step at the latest, so it cannot skip choosing Meals again.
 */
fun Itinerary.withFittingMealBlocks(zone: ZoneId): Itinerary {
    val fitting = mealBlocks.filter { it.fitsWindow(this, zone) }
    if (fitting.size == mealBlocks.size) return this
    return copy(mealBlocks = fitting, step = minOf(step, PlanningStep.Meals))
}
