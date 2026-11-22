package com.example.hackapp.data.repository

import android.content.Context
import com.example.hackapp.data.Event
import com.example.hackapp.data.EventStatus
import com.example.hackapp.data.parseEventTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

/** Filters for an Event search. Every filter is optional; bounds apply to the Event's start. */
data class EventQuery(
    val districtIds: Set<String>? = null,
    val startsAtOrAfter: OffsetDateTime? = null,
    val startsBefore: OffsetDateTime? = null,
    val includeUnavailable: Boolean = false,
)

/**
 * Data boundary for Events. Screens and the planner depend only on this interface, so the bundled
 * snapshot can later be replaced by Firestore or an API without changing them.
 */
interface EventRepository {
    /** Matching Events, ordered by start time. */
    suspend fun search(query: EventQuery = EventQuery()): List<Event>
    suspend fun getEvent(id: String): Event?
    /** Number of matching Events per District id. Districts with no match are absent. */
    suspend fun countByDistrict(query: EventQuery = EventQuery()): Map<String, Int>
}

/** File format of `assets/citypacks/<city>/events.json`. */
@Serializable
data class EventSnapshot(
    val cityId: String,
    val generatedAt: String,
    val events: List<Event>,
)

/** [EventRepository] over a bundled snapshot. [readSnapshot] supplies the file's text. */
class SnapshotEventRepository(
    private val cityId: String,
    private val readSnapshot: suspend () -> String,
) : EventRepository {

    private data class IndexedEvent(val event: Event, val start: OffsetDateTime)

    private val loadLock = Mutex()
    @Volatile private var loaded: List<IndexedEvent>? = null

    override suspend fun search(query: EventQuery): List<Event> =
        indexed().filter { it.matches(query) }.map { it.event }

    override suspend fun getEvent(id: String): Event? =
        indexed().firstOrNull { it.event.id == id }?.event

    override suspend fun countByDistrict(query: EventQuery): Map<String, Int> =
        indexed().asSequence()
            .filter { it.matches(query) }
            .mapNotNull { it.event.districtId }
            .groupingBy { it }
            .eachCount()

    private suspend fun indexed(): List<IndexedEvent> = loaded ?: loadLock.withLock {
        loaded ?: parse(readSnapshot()).also { loaded = it }
    }

    private fun parse(text: String): List<IndexedEvent> {
        val snapshot = SnapshotJson.decodeFromString<EventSnapshot>(text)
        require(snapshot.cityId == cityId) {
            "Event snapshot is for '${snapshot.cityId}', expected '$cityId'"
        }
        return snapshot.events
            .mapNotNull { event -> parseStart(event)?.let { IndexedEvent(event, it) } }
            .sortedBy { it.start }
    }

    /** Events without a full start timestamp cannot be planned, so they are not served. */
    private fun parseStart(event: Event): OffsetDateTime? = parseEventTime(event.schedule.startDate)

    private fun IndexedEvent.matches(query: EventQuery): Boolean =
        (query.includeUnavailable || event.status == EventStatus.SCHEDULED) &&
            (query.districtIds == null || event.districtId in query.districtIds) &&
            (query.startsAtOrAfter == null || !start.isBefore(query.startsAtOrAfter)) &&
            (query.startsBefore == null || start.isBefore(query.startsBefore))

    companion object {
        private val SnapshotJson = Json { ignoreUnknownKeys = true }

        fun fromAsset(context: Context, cityId: String, assetPath: String): SnapshotEventRepository {
            val appContext = context.applicationContext
            return SnapshotEventRepository(cityId) {
                withContext(Dispatchers.IO) {
                    appContext.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            }
        }
    }
}
