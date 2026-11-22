package com.example.hackapp.domain

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A Stamp earned for a confirmed Stop. The Passport will read these later. */
@Serializable
data class EarnedStamp(val stopId: String, val name: String, val district: String, val date: String)

private val Context.tripDataStore by preferencesDataStore(name = "trip")

/**
 * The Itinerary being followed and the Stamps earned so far, on the device. The progress survives
 * the app being closed, and the notification buttons update it without the UI running.
 */
class UnderwayStore(context: Context) {
    private val store = context.applicationContext.tripDataStore
    private val json = Json { ignoreUnknownKeys = true }

    val progress: Flow<UnderwayProgress?> = store.data.map { prefs ->
        prefs[PROGRESS]?.let { runCatching { json.decodeFromString<UnderwayProgress>(it) }.getOrNull() }
    }

    val stamps: Flow<List<EarnedStamp>> = store.data.map { prefs ->
        prefs[STAMPS]?.let { runCatching { json.decodeFromString<List<EarnedStamp>>(it) }.getOrNull() }.orEmpty()
    }

    suspend fun current(): UnderwayProgress? = progress.first()

    suspend fun save(progress: UnderwayProgress) {
        store.edit { it[PROGRESS] = json.encodeToString(progress) }
    }

    suspend fun clear() {
        store.edit { it.remove(PROGRESS) }
    }

    suspend fun addStamps(new: List<EarnedStamp>) {
        if (new.isEmpty()) return
        store.edit { prefs ->
            val old = prefs[STAMPS]?.let { runCatching { json.decodeFromString<List<EarnedStamp>>(it) }.getOrNull() }.orEmpty()
            val merged = old + new.filter { n -> old.none { it.stopId == n.stopId && it.date == n.date } }
            prefs[STAMPS] = json.encodeToString(merged)
        }
    }

    /** Updates the progress in place (used by both the screens and the notification buttons). */
    suspend fun update(change: (UnderwayProgress) -> UnderwayProgress): UnderwayProgress? {
        val now = current() ?: return null
        return change(now).also { save(it) }
    }

    private companion object {
        val PROGRESS = stringPreferencesKey("progress")
        val STAMPS = stringPreferencesKey("stamps")
    }
}

/** Moves to the next Stop once the current one is [status]; [UnderwayProgress.atStop] resets. */
fun UnderwayProgress.finishCurrent(status: StopStatus): UnderwayProgress {
    if (finished) return this
    val updated = statuses.toMutableList().also { it[index] = status }
    return copy(statuses = updated, index = index + 1, atStop = false)
}

fun UnderwayProgress.arrived(): UnderwayProgress = copy(atStop = true)
