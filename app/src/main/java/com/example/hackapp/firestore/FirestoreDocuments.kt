package com.example.hackapp.firestore

import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryCodec
import com.example.hackapp.itinerary.ShowtimeKey
import com.example.hackapp.itinerary.TicketPriceSummary
import com.example.hackapp.itinerary.isValidTicketPrice
import com.example.hackapp.profile.Profile
import com.example.hackapp.profile.ProfileCodec
import java.security.MessageDigest

/**
 * Pure mapping between the app models and Firestore documents. No Firebase types, so it is unit
 * tested on the JVM. Layout (see `firestore.rules`):
 *
 * ```
 * users/{uid}/profile/current          { schemaVersion, ownerUid, payload, updatedAtMillis }
 * users/{uid}/itineraries/{id}         { schemaVersion, ownerUid, id, payload, updatedAtMillis }
 * ticketReports/{keyId}/reports/{uid}  { ownerUid, priceCad, updatedAt }   owner-only, never listed
 * ticketSummaries/{keyId}              { medianCad, reportCount }          written by a Cloud Function only
 * ```
 * `payload` is the JSON text from the existing [ProfileCodec] and [ItineraryCodec], so the model
 * stays the single source of truth and `schemaVersion` lets a future model change be migrated.
 */
internal object FirestoreDocuments {
    const val USERS = "users"
    const val PROFILE = "profile"
    const val PROFILE_DOC = "current"
    const val ITINERARIES = "itineraries"
    const val TICKET_REPORTS = "ticketReports"
    const val REPORTS = "reports"
    const val TICKET_SUMMARIES = "ticketSummaries"

    /** Bump when the stored model changes in a way old readers cannot decode. */
    const val SCHEMA_VERSION = 1

    fun profileDoc(uid: String, profile: Profile, nowMillis: Long): Map<String, Any?> = mapOf(
        "schemaVersion" to SCHEMA_VERSION,
        "ownerUid" to uid,
        "payload" to ProfileCodec.encode(profile),
        "updatedAtMillis" to nowMillis,
    )

    /** Null for a missing, newer-version, foreign or unreadable document: onboarding can run again. */
    fun profileFrom(uid: String, doc: Map<String, Any?>?): Profile? {
        val payload = payloadOf(doc, uid) ?: return null
        return ProfileCodec.decode(payload)
    }

    fun itineraryDoc(itinerary: Itinerary): Map<String, Any?> = mapOf(
        "schemaVersion" to SCHEMA_VERSION,
        "ownerUid" to itinerary.ownerUid,
        "id" to itinerary.id,
        "payload" to ItineraryCodec.encode(itinerary),
        "updatedAtMillis" to itinerary.updatedAtMillis,
    )

    /** Null when unreadable or when the stored owner or id does not match where it was found. */
    fun itineraryFrom(uid: String, docId: String, doc: Map<String, Any?>?): Itinerary? {
        val payload = payloadOf(doc, uid) ?: return null
        val itinerary = ItineraryCodec.decode(payload) ?: return null
        return itinerary.takeIf { it.ownerUid == uid && it.id == docId && doc?.get("id") == docId }
    }

    private fun payloadOf(doc: Map<String, Any?>?, uid: String): String? {
        if (doc == null) return null
        val version = (doc["schemaVersion"] as? Number)?.toInt() ?: return null
        if (version != SCHEMA_VERSION) return null
        if (doc["ownerUid"] != uid) return null
        return doc["payload"] as? String
    }

    /** Document ids cannot contain '/', and a key holds free text, so the id is a SHA-256 hex digest. */
    fun keyId(key: ShowtimeKey): String =
        MessageDigest.getInstance("SHA-256").digest(key.value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** [timestamp] is the server timestamp sentinel; the rules require it to equal request.time. */
    fun reportDoc(uid: String, priceCad: Double, timestamp: Any): Map<String, Any?> = mapOf(
        "ownerUid" to uid,
        "priceCad" to priceCad,
        "updatedAt" to timestamp,
    )

    fun reportFrom(uid: String, doc: Map<String, Any?>?): Double? {
        if (doc == null || doc["ownerUid"] != uid) return null
        val price = (doc["priceCad"] as? Number)?.toDouble() ?: return null
        return price.takeIf(::isValidTicketPrice)
    }

    /** Null when absent or malformed, so a bad aggregate never shows a wrong median. */
    fun summaryFrom(doc: Map<String, Any?>?): TicketPriceSummary? {
        if (doc == null) return null
        val median = (doc["medianCad"] as? Number)?.toDouble() ?: return null
        val count = (doc["reportCount"] as? Number)?.toInt() ?: return null
        return if (isValidTicketPrice(median) && count > 0) TicketPriceSummary(median, count) else null
    }
}
