package com.example.hackapp.itinerary

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Stores a person's Itineraries. Screens depend on this interface, so Firestore can replace it. */
interface ItineraryRepository {
    /** All of this person's Itineraries, most recently updated first. */
    fun observeItineraries(uid: String): Flow<List<Itinerary>>
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
