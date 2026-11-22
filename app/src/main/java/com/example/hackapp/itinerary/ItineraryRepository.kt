package com.example.hackapp.itinerary

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Stores a person's Itineraries. Screens depend on this interface, so Firestore can replace it. */
interface ItineraryRepository {
    /** All of this person's Itineraries, most recently updated first. */
    fun observeItineraries(uid: String): Flow<List<Itinerary>>
    /**
     * One-shot read, most recently updated first. [freshOnly] asks for server-confirmed data and
     * throws when that is impossible (for example offline); local stores ignore it.
     */
    suspend fun listItineraries(uid: String, freshOnly: Boolean = false): List<Itinerary> =
        observeItineraries(uid).first()

    /**
     * Saves [itinerary] only if the stored copy still has `updatedAtMillis == expectedUpdatedAtMillis`
     * (null means "must not exist yet"). Returns false, saving nothing, when another device changed
     * it first; the caller reloads, re-applies its edit and retries. Use it for every
     * read-modify-write. The default is check-then-save, which is enough for one local store; the
     * Firestore version does it in a transaction, which needs a connection and throws offline.
     */
    suspend fun saveItineraryIfUnchanged(itinerary: Itinerary, expectedUpdatedAtMillis: Long?): Boolean {
        val stored = getItinerary(itinerary.ownerUid, itinerary.id)?.updatedAtMillis
        if (!preconditionHolds(stored, expectedUpdatedAtMillis)) return false
        saveItinerary(itinerary)
        return true
    }
    suspend fun getItinerary(uid: String, id: String): Itinerary?
    suspend fun saveItinerary(itinerary: Itinerary)
    suspend fun deleteItinerary(uid: String, id: String)
}

/** Keeps Itineraries for this process only. Used by previews and tests. */
class InMemoryItineraryRepository : ItineraryRepository {
    private val items = MutableStateFlow<Map<String, Itinerary>>(emptyMap())

    override fun observeItineraries(uid: String): Flow<List<Itinerary>> = items.map { all ->
        all.values.filter { it.ownerUid == uid }.sortedByDescending { it.updatedAtMillis }
    }

    override suspend fun getItinerary(uid: String, id: String): Itinerary? =
        items.value[id]?.takeIf { it.ownerUid == uid }

    override suspend fun saveItinerary(itinerary: Itinerary) {
        items.update { it + (itinerary.id to itinerary) }
    }

    override suspend fun deleteItinerary(uid: String, id: String) {
        items.update { all -> if (all[id]?.ownerUid == uid) all - id else all }
    }
}

val LocalItineraryRepository = staticCompositionLocalOf<ItineraryRepository> { InMemoryItineraryRepository() }

/** The stored version (null when absent) must be exactly the one the edit was based on. */
fun preconditionHolds(storedUpdatedAtMillis: Long?, expectedUpdatedAtMillis: Long?): Boolean =
    storedUpdatedAtMillis == expectedUpdatedAtMillis
