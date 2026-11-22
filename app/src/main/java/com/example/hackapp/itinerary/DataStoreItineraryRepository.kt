package com.example.hackapp.itinerary

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.DateTimeException

internal object ItineraryCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(itinerary: Itinerary): String = json.encodeToString(Itinerary.serializer(), itinerary)

    /** An unreadable record counts as missing, so one bad Itinerary cannot hide the others. */
    fun decode(text: String): Itinerary? = try {
        json.decodeFromString(Itinerary.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: DateTimeException) {
        null
    }
}

/** Stores one record per account and Itinerary. */
class DataStoreItineraryRepository(private val store: DataStore<Preferences>) : ItineraryRepository {

    override fun observeItineraries(uid: String): Flow<List<Itinerary>> = store.data
        .map { preferences -> decodeAll(preferences, uid) }
        .distinctUntilChanged()

    override suspend fun getItinerary(uid: String, id: String): Itinerary? =
        store.data.first()[keyFor(uid, id)]
            ?.let(ItineraryCodec::decode)
            ?.takeIf { it.ownerUid == uid && it.id == id }

    override suspend fun saveItinerary(itinerary: Itinerary) {
        store.edit { it[keyFor(itinerary.ownerUid, itinerary.id)] = ItineraryCodec.encode(itinerary) }
    }

    override suspend fun deleteItinerary(uid: String, id: String) {
        store.edit { it.remove(keyFor(uid, id)) }
    }

    private fun decodeAll(preferences: Preferences, uid: String): List<Itinerary> {
        val prefix = prefixFor(uid)
        return preferences.asMap().asSequence()
            .filter { (key, _) -> key.name.startsWith(prefix) }
            .mapNotNull { (_, value) -> (value as? String)?.let(ItineraryCodec::decode) }
            .filter { it.ownerUid == uid }
            .sortedByDescending { it.updatedAtMillis }
            .toList()
    }

    /** The length keeps one account's prefix from matching another account's uid. */
    private fun prefixFor(uid: String) = "itinerary_${uid.length}_${uid}_"

    private fun keyFor(uid: String, id: String) = stringPreferencesKey(prefixFor(uid) + id)
}

private val Context.itineraryDataStore by preferencesDataStore(name = "itineraries")

fun createItineraryRepository(context: Context): ItineraryRepository =
    DataStoreItineraryRepository(context.applicationContext.itineraryDataStore)
