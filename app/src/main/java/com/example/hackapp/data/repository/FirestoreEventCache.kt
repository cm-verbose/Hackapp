package com.example.hackapp.data.repository

import android.util.Log
import com.example.hackapp.data.Event
import com.example.hackapp.data.remote.Network
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.JsonObject

/**
 * Firestore-backed cache in front of the event APIs.
 *
 * Layout:
 * ```
 * eventSources/{sourceKey}                 { sourceKey, eventCount, storedAt }
 * eventSources/{sourceKey}/events/{id}     Event fields
 * ```
 * The `eventSources/{sourceKey}` doc is written **last**, so it only exists once
 * every event was stored. If it exists, events are read from Firestore and the API
 * is never called; existing data is never overwritten.
 */
class FirestoreEventCache(
    private val db: FirebaseFirestore = Firebase.firestore,
) {

    /**
     * Returns events for [sourceKey] from Firestore if already stored; otherwise calls
     * [fetchFromApi], stores the result, and returns it.
     * Firestore failures fall back to the API so the app still works offline / unauthenticated.
     */
    suspend fun getOrFetch(sourceKey: String, fetchFromApi: suspend () -> List<Event>): List<Event> {
        val sourceDoc = db.collection(SOURCES).document(sourceKey)

        val alreadyStored = try {
            sourceDoc.get().await().exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "[$sourceKey] Firestore check failed, using API without caching", e)
            return fetchFromApi()
        }

        if (alreadyStored) {
            try {
                val events = readEvents(sourceKey)
                Log.d(TAG, "[$sourceKey] Loaded ${events.size} events from Firestore")
                return events
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "[$sourceKey] Firestore read failed, falling back to API", e)
                return fetchFromApi()
            }
        }

        val events = fetchFromApi()
        Log.d(TAG, "[$sourceKey] Fetched ${events.size} events from API, storing in Firestore")
        try {
            storeEvents(sourceKey, events)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[$sourceKey] Failed to store events in Firestore", e)
        }
        return events
    }

    private suspend fun readEvents(sourceKey: String): List<Event> {
        val snapshot = eventsCollection(sourceKey).get().await()
        return snapshot.documents.mapNotNull { doc ->
            runCatching {
                Network.json.decodeFromJsonElement(Event.serializer(), FirestoreJson.fromFirestore(doc.data))
            }.onFailure { Log.w(TAG, "[$sourceKey] Skipping unreadable doc ${doc.id}", it) }
                .getOrNull()
        }
    }

    private suspend fun storeEvents(sourceKey: String, events: List<Event>) {
        val collection = eventsCollection(sourceKey)
        // Some sources (Has Things showtimes) can repeat ids; keep the first.
        val unique = events.distinctBy { it.id }

        unique.chunked(BATCH_SIZE).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { event ->
                val json = Network.json.encodeToJsonElement(Event.serializer(), event) as JsonObject
                batch.set(collection.document(event.id.toDocumentId()), FirestoreJson.toFirestore(json)!!)
            }
            batch.commit().await()
        }

        // Marker written last: its presence means the source is fully stored.
        db.collection(SOURCES).document(sourceKey).set(
            mapOf(
                "sourceKey" to sourceKey,
                "eventCount" to unique.size,
                "storedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Log.d(TAG, "[$sourceKey] Stored ${unique.size} events in Firestore")
    }

    private fun eventsCollection(sourceKey: String) =
        db.collection(SOURCES).document(sourceKey).collection(EVENTS)

    /** Firestore ids can't contain '/' and can't be "." or "..". */
    private fun String.toDocumentId(): String =
        replace('/', '_').takeUnless { it == "." || it == ".." } ?: "_$this"

    companion object {
        private const val TAG = "FirestoreEventCache"
        private const val SOURCES = "eventSources"
        private const val EVENTS = "events"
        private const val BATCH_SIZE = 400 // Firestore limit is 500 writes per batch

        const val TORONTO_HAS_THINGS = "toronto-hasthings"
        const val TORONTO_CITY_CSV = "toronto-city-csv"
        const val MONTREAL_HAS_THINGS = "montreal-hasthings"
        const val MONTREAL_OPEN_DATA = "montreal-opendata"
    }
}
