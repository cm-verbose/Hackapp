package com.example.hackapp.underway

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Persists one [UnderwayState] per Itinerary, so Underway survives process death. */
interface UnderwayRepository {
    suspend fun get(uid: String, itineraryId: String): UnderwayState?
    suspend fun save(state: UnderwayState)
    fun observe(uid: String): Flow<List<UnderwayState>>
    /** Every account's states. Used after boot, when no one is signed in yet. */
    suspend fun all(): List<UnderwayState>
}

class InMemoryUnderwayRepository : UnderwayRepository {
    private val items = MutableStateFlow<Map<String, UnderwayState>>(emptyMap())

    override suspend fun get(uid: String, itineraryId: String) =
        items.value[itineraryId]?.takeIf { it.ownerUid == uid }

    override suspend fun save(state: UnderwayState) {
        items.update { it + (state.itineraryId to state) }
    }

    override fun observe(uid: String): Flow<List<UnderwayState>> =
        items.map { all -> all.values.filter { it.ownerUid == uid } }

    override suspend fun all() = items.value.values.toList()
}

internal object UnderwayCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(state: UnderwayState): String = json.encodeToString(UnderwayState.serializer(), state)

    /** An unreadable record counts as missing. */
    fun decode(text: String): UnderwayState? = try {
        json.decodeFromString(UnderwayState.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

class DataStoreUnderwayRepository(private val store: DataStore<Preferences>) : UnderwayRepository {

    override suspend fun get(uid: String, itineraryId: String): UnderwayState? =
        store.data.first()[keyFor(uid, itineraryId)]?.let(UnderwayCodec::decode)
            ?.takeIf { it.ownerUid == uid && it.itineraryId == itineraryId }

    override suspend fun save(state: UnderwayState) {
        store.edit { it[keyFor(state.ownerUid, state.itineraryId)] = UnderwayCodec.encode(state) }
    }

    override fun observe(uid: String): Flow<List<UnderwayState>> =
        store.data.map { prefs -> decodeAll(prefs).filter { it.ownerUid == uid } }.distinctUntilChanged()

    override suspend fun all(): List<UnderwayState> = decodeAll(store.data.first())

    private fun decodeAll(prefs: Preferences): List<UnderwayState> = prefs.asMap().asSequence()
        .filter { (key, _) -> key.name.startsWith("underway_") }
        .mapNotNull { (_, value) -> (value as? String)?.let(UnderwayCodec::decode) }
        .toList()

    private fun keyFor(uid: String, id: String) = stringPreferencesKey("underway_${uid.length}_${uid}_$id")
}

private val Context.underwayDataStore by preferencesDataStore(name = "underway")

fun createUnderwayRepository(context: Context): UnderwayRepository =
    DataStoreUnderwayRepository(context.applicationContext.underwayDataStore)
