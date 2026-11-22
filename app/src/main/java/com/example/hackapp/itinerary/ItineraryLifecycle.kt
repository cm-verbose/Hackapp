package com.example.hackapp.itinerary

import com.example.hackapp.services.LegEstimator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Moves Planning drafts whose time window has ended to Archived, and returns them. */
suspend fun archiveExpiredDrafts(
    repository: ItineraryRepository,
    uid: String,
    now: Instant,
    zone: ZoneId,
): List<Itinerary> {
    val expired = repository.listItineraries(uid, freshOnly = true)
        .filter { it.phase == ItineraryPhase.Planning && !now.isBefore(it.windowEnd(zone).toInstant()) }
        .map { it.copy(phase = ItineraryPhase.Archived, updatedAtMillis = now.toEpochMilli()) }
    expired.forEach { repository.saveItinerary(it) }
    return expired
}

/** Shows which Stops fit [newDate]. Nothing is saved until [restoreArchivedDraft]. */
fun previewRestore(
    archived: Itinerary,
    newDate: LocalDate,
    estimator: LegEstimator,
    zone: ZoneId,
    isMealOpen: (Stop, ZonedDateTime) -> Boolean = { _, _ -> true },
): DraftEditPreview = previewDraftEdit(archived, DraftEdit.ChangeDate(newDate), estimator, zone, isMealOpen)

/**
 * Saves a confirmed restore back to Planning. Returns null, saving nothing, when the draft is not
 * Archived or its new time window has already ended.
 */
suspend fun restoreArchivedDraft(
    repository: ItineraryRepository,
    preview: DraftEditPreview,
    now: Instant,
    zone: ZoneId,
): Itinerary? {
    if (preview.original.phase != ItineraryPhase.Archived) return null
    val proposed = preview.proposed
    if (!now.isBefore(proposed.windowEnd(zone).toInstant())) return null
    val step = if (proposed.step == PlanningStep.Review && !canFinish(proposed)) {
        PlanningStep.Stops
    } else {
        proposed.step
    }
    val restored = proposed.copy(
        phase = ItineraryPhase.Planning,
        step = step,
        updatedAtMillis = now.toEpochMilli(),
    )
    repository.saveItinerary(restored)
    return restored
}

enum class StartBlock { NotInPlanning, NoEvents, WrongDate, WindowEnded }

/**
 * Null when the Itinerary may start. Outside demo mode it starts only on its own date, which
 * includes the hours after midnight of its time window.
 */
fun startBlock(itinerary: Itinerary, now: Instant, zone: ZoneId, demoMode: Boolean = false): StartBlock? {
    if (itinerary.phase != ItineraryPhase.Planning) return StartBlock.NotInPlanning
    if (!canFinish(itinerary)) return StartBlock.NoEvents
    if (demoMode) return null
    val windowEnd = itinerary.windowEnd(zone).toInstant()
    val today = now.atZone(zone).toLocalDate()
    return when {
        today.isBefore(itinerary.date) -> StartBlock.WrongDate
        now.isBefore(windowEnd) -> null
        else -> StartBlock.WindowEnded
    }
}

/** Ends an Underway Itinerary: confirmed Stops stay confirmed and the rest become Skipped. */
fun completeUnderway(itinerary: Itinerary, now: Instant): Itinerary = itinerary.copy(
    phase = ItineraryPhase.Completed,
    stops = itinerary.stops.map {
        if (it.status == StopStatus.Confirmed) it else it.copy(status = StopStatus.Skipped)
    },
    updatedAtMillis = now.toEpochMilli(),
)

sealed interface StartResult {
    /** [endedItinerary] is the one moved to Completed to make room, if any. */
    data class Started(val itinerary: Itinerary, val endedItinerary: Itinerary?) : StartResult
    data class Blocked(val reason: StartBlock) : StartResult
    /** Another Itinerary is Underway. Call again with `confirmEndCurrent = true` after warning. */
    data class NeedsConfirmation(val current: Itinerary) : StartResult
    data object NotFound : StartResult
}

/** Starts a draft, keeping at most one Itinerary Underway. */
suspend fun startItinerary(
    repository: ItineraryRepository,
    uid: String,
    id: String,
    now: Instant,
    zone: ZoneId,
    demoMode: Boolean = false,
    confirmEndCurrent: Boolean = false,
): StartResult {
    val draft = repository.getItinerary(uid, id) ?: return StartResult.NotFound
    startBlock(draft, now, zone, demoMode)?.let { return StartResult.Blocked(it) }

    val underway = repository.listItineraries(uid)
        .filter { it.phase == ItineraryPhase.Underway && it.id != id }
    if (underway.isNotEmpty() && !confirmEndCurrent) return StartResult.NeedsConfirmation(underway.first())

    // End the old one first: an interruption here can never leave two Itineraries Underway.
    val ended = underway.map { completeUnderway(it, now) }
    ended.forEach { repository.saveItinerary(it) }
    val started = draft.copy(phase = ItineraryPhase.Underway, updatedAtMillis = now.toEpochMilli())
    repository.saveItinerary(started)
    return StartResult.Started(started, ended.firstOrNull())
}
