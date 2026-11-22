package com.example.hackapp.passport

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.hackapp.itinerary.Itinerary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.DateTimeException

internal object StampCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(stamp: PassportStamp): String = json.encodeToString(PassportStamp.serializer(), stamp)

    /** An unreadable record counts as missing, so one bad Stamp cannot hide the others. */
    fun decode(text: String): PassportStamp? = try {
        json.decodeFromString(PassportStamp.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: DateTimeException) {
        null
    }
}

/** Stores one record per account and Stamp key. Awarding runs inside one DataStore edit. */
class DataStoreStampRepository(private val store: DataStore<Preferences>) : StampRepository {

    override fun observeStamps(uid: String): Flow<List<PassportStamp>> = store.data
        .map { decodeAll(it, uid) }
        .distinctUntilChanged()

    override suspend fun award(itinerary: Itinerary, resolver: StampIdentityResolver, awardedAtMillis: Long): StampAward {
        lateinit var award: StampAward
        store.edit { preferences ->
            val existing = decodeAll(preferences, itinerary.ownerUid)
            award = awardStamps(itinerary, existing, resolver, awardedAtMillis)
            award.created.forEach { preferences[keyFor(it.ownerUid, it.key)] = StampCodec.encode(it) }
        }
        return award
    }

    private fun decodeAll(preferences: Preferences, uid: String): List<PassportStamp> {
        val prefix = prefixFor(uid)
        return preferences.asMap().asSequence()
            .filter { (key, _) -> key.name.startsWith(prefix) }
            .mapNotNull { (_, value) -> (value as? String)?.let(StampCodec::decode) }
            .filter { it.ownerUid == uid }
            .toList()
            .inPassportOrder()
    }

    /** The length keeps one account's prefix from matching another account's uid. */
    private fun prefixFor(uid: String) = "stamp_${uid.length}_${uid}_"

    private fun keyFor(uid: String, stampKey: String) = stringPreferencesKey(prefixFor(uid) + stampKey)
}

private val Context.stampDataStore by preferencesDataStore(name = "stamps")

fun createStampRepository(context: Context): StampRepository =
    DataStoreStampRepository(context.applicationContext.stampDataStore)
