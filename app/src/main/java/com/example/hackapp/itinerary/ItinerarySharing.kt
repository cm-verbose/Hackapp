package com.example.hackapp.itinerary

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

/** Shared payloads above this size are refused, so a throw always fits one Nearby message. */
const val MAX_SHARED_ITINERARY_CHARS = 30_000

private const val SHARE_FORMAT_VERSION = 1

private val ShareJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

/**
 * An Itinerary sent to a nearby phone. It is a copy: after the handoff each person plans, travels
 * and earns Stamps on their own copy, and nothing is synced back.
 */
@Serializable
data class SharedItinerary(
    val version: Int = SHARE_FORMAT_VERSION,
    val itinerary: Itinerary,
)

/** Only Itineraries still ahead can be shared: drafts and the one Underway. */
val Itinerary.canShare: Boolean
    get() = phase == ItineraryPhase.Planning || phase == ItineraryPhase.Underway

/**
 * The payload for a throw, or null when this Itinerary cannot be shared or would not fit. Personal
 * data stays behind: the owner, own ticket price reports and budget overrides.
 */
fun Itinerary.toSharePayload(): String? {
    if (!canShare) return null
    val sent = copy(
        ownerUid = "",
        eventBudget = null,
        mealBudgets = null,
        stops = stops.map { it.copy(ticketPriceCad = null) },
    )
    return ShareJson.encodeToString(SharedItinerary.serializer(), SharedItinerary(itinerary = sent))
        .takeIf { it.length <= MAX_SHARED_ITINERARY_CHARS }
}

/** Reads a received payload. Null for anything that is not a shared Itinerary this version understands. */
fun decodeSharedItinerary(payload: String): SharedItinerary? {
    if (payload.length > MAX_SHARED_ITINERARY_CHARS) return null
    val shared = runCatching { ShareJson.decodeFromString(SharedItinerary.serializer(), payload) }.getOrNull()
    return shared?.takeIf { it.version == SHARE_FORMAT_VERSION && it.itinerary.stops.size <= MAX_SHARED_STOPS }
}

private const val MAX_SHARED_STOPS = 40

/**
 * The receiver's own copy of [shared], with a new id. It is always a draft in Planning, so the
 * receiver reviews it and taps Start themselves.
 *
 * A draft keeps its step. An Itinerary already Underway opens on Review: Stops the sender already
 * left by [now] are marked skipped, so the receiver joins at the current or next Stop. No Stop is
 * ever copied as confirmed, so a Stamp is only earned by actually going.
 */
fun importSharedItinerary(shared: SharedItinerary, uid: String, cityId: String, now: Instant): Itinerary? {
    val source = shared.itinerary
    if (source.cityId != cityId || source.districtIds.isEmpty() || !source.canShare) return null
    val underway = source.phase == ItineraryPhase.Underway
    val stops = source.stops.map { stop ->
        val past = !stop.plannedDeparture.toInstant().isAfter(now)
        val status = when {
            stop.status == StopStatus.Skipped -> StopStatus.Skipped
            underway && past -> StopStatus.Skipped
            else -> StopStatus.Planned
        }
        stop.copy(status = status, ticketPriceCad = null)
    }
    // An Itinerary that has nothing left to join is not worth adding.
    if (underway && stops.none { it.status == StopStatus.Planned }) return null
    val millis = now.toEpochMilli()
    return source.copy(
        id = UUID.randomUUID().toString(),
        ownerUid = uid,
        phase = ItineraryPhase.Planning,
        step = if (underway) PlanningStep.Review else source.step,
        stops = stops,
        eventBudget = null,
        mealBudgets = null,
        createdAtMillis = millis,
        updatedAtMillis = millis,
    )
}
