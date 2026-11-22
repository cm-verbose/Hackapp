package com.example.hackapp.itinerary

import com.example.hackapp.data.Event
import com.example.hackapp.services.LegEstimator
import java.time.ZoneId

/**
 * What removing one Stop does to the rest. [affected] are the later Stops that transit from the
 * previous remaining Stop cannot reach by their start. Nothing here is saved.
 */
data class StopRemovalPlan(
    val itinerary: Itinerary,
    val removed: Stop,
    /** In visit order. Empty means every later Stop is still reachable. */
    val affected: List<Stop>,
    /** The last remaining Stop before the gap, or null when the gap is at the start of the window. */
    val afterStopId: String?,
    /**
     * The Itinerary with [removed] and the [affected] Stops gone and the later reachable Stops kept.
     * Back at the Stops step, so selection can fill the gap.
     */
    val reorganized: Itinerary,
) {
    val hasAffected: Boolean get() = affected.isNotEmpty()
}

/** Null when [stopId] is not a Stop of this Itinerary. */
fun planStopRemoval(
    itinerary: Itinerary,
    stopId: String,
    estimator: LegEstimator,
    zone: ZoneId,
): StopRemovalPlan? {
    val removed = itinerary.stops.firstOrNull { it.id == stopId } ?: return null
    val preview = previewDraftEdit(itinerary, DraftEdit.RemoveStop(stopId), estimator, zone)
    val affected = preview.removed.filter { it.reason != StopRemovalReason.RemovedByPerson }.map { it.stop }
    val previous = preview.kept.lastOrNull {
        it.status != StopStatus.Skipped && it.start.toInstant().isBefore(removed.start.toInstant())
    }
    val reorganized = preview.proposed.copy(step = minOf(itinerary.step, PlanningStep.Stops))
    return StopRemovalPlan(itinerary, removed, affected, previous?.id, reorganized)
}

/**
 * Other showtimes of [affected]'s title and venue that fit [base], the Itinerary without the
 * affected Stops. Each must be reachable from the Stop before it and leave time to reach the next.
 * Empty for a Meal, whose time is its Meal Block, or an Event that is gone from [events].
 */
fun rescheduleOptions(
    base: Itinerary,
    affected: Stop,
    events: List<Event>,
    context: PlanningContext,
    estimator: LegEstimator,
): List<ReachableShowtime> {
    if (affected.kind != StopKind.Event) return emptyList()
    val original = events.firstOrNull { it.id == affected.sourceId } ?: return emptyList()
    val key = original.showtimeKey()
    val others = events.filter { it.id != original.id && it.showtimeKey() == key }
    val active = base.activeStops
    val gap = TimeGap(
        previous = active.lastOrNull { it.start.toInstant().isBefore(affected.start.toInstant()) },
        next = active.firstOrNull { !it.start.toInstant().isBefore(affected.start.toInstant()) },
    )
    val search = EventSearch(base, others, context, affected.districtId, gap)
    return reachableShowtimes(search, estimator)
}

/** [base] with [affected] replaced by [option]. Null when the option cannot become a Stop. */
fun rescheduled(base: Itinerary, affected: Stop, option: ReachableShowtime): Itinerary? {
    val stop = eventStop(option.event, option.suggestedDeparture.plannedDeparture, affected.ticketPriceCad)
        ?: return null
    return base.withStop(stop)
}
