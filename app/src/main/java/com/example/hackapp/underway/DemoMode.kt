package com.example.hackapp.underway

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.activeStops
import com.example.hackapp.itinerary.defaultEstimate
import com.example.hackapp.itinerary.latLng
import com.example.hackapp.services.LegEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * What survives process death. [ownerUid] is the account that turned demo mode on.
 * [runItineraryId] is the demo run being started, so a run that lost its state is still known.
 */
data class DemoSnapshot(
    val simulatedMillis: Long,
    val ownerUid: String? = null,
    val runItineraryId: String? = null,
)

/** Keeps demo mode across process death. [load] is synchronous: the clock needs it at once. */
interface DemoModeRepository {
    fun load(): DemoSnapshot?
    suspend fun save(snapshot: DemoSnapshot)
    suspend fun clear()
}

class InMemoryDemoModeRepository(private var snapshot: DemoSnapshot? = null) : DemoModeRepository {
    override fun load() = snapshot
    override suspend fun save(snapshot: DemoSnapshot) { this.snapshot = snapshot }
    override suspend fun clear() { snapshot = null }
}

class SharedPreferencesDemoModeRepository(context: Context) : DemoModeRepository {
    private val prefs = context.applicationContext.getSharedPreferences("demo_mode", Context.MODE_PRIVATE)

    override fun load(): DemoSnapshot? {
        if (!prefs.contains(KEY_SIMULATED)) return null
        return DemoSnapshot(
            simulatedMillis = prefs.getLong(KEY_SIMULATED, 0L),
            ownerUid = prefs.getString(KEY_OWNER, null),
            runItineraryId = prefs.getString(KEY_RUN, null),
        )
    }

    override suspend fun save(snapshot: DemoSnapshot) = commit {
        putLong(KEY_SIMULATED, snapshot.simulatedMillis)
        putString(KEY_OWNER, snapshot.ownerUid)
        putString(KEY_RUN, snapshot.runItineraryId)
    }

    override suspend fun clear() = commit {
        remove(KEY_SIMULATED)
        remove(KEY_OWNER)
        remove(KEY_RUN)
    }

    private suspend fun commit(change: SharedPreferences.Editor.() -> Unit) = withContext(Dispatchers.IO) {
        val editor = prefs.edit()
        editor.change()
        if (!editor.commit()) throw IOException("Could not save demo mode")
    }

    private companion object {
        const val KEY_SIMULATED = "simulated_millis"
        const val KEY_OWNER = "owner_uid"
        const val KEY_RUN = "run_itinerary_id"
    }
}

/**
 * The app's one clock. Real time when demo mode is off. When on, simulated time runs only while
 * the app is in the foreground ([resume], [pause]) and restarts paused at its saved time.
 * Foreground time is measured with [elapsed], so a wall-clock change cannot move simulated time.
 */
class DemoClock(
    private val repository: DemoModeRepository,
    override val zone: ZoneId,
    /** The date a new draft defaults to in demo mode. */
    val pinnedDate: LocalDate,
    /** The first day with Events: nothing reads as past while planning in demo mode. */
    private val planningFrom: LocalDate,
    private val real: () -> Instant = Instant::now,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
) : UnderwayClock {
    /** Simulated [millis] at the elapsed time [since]. A null [since] means paused. */
    private class Position(val millis: Long, val since: Long?) {
        fun at(elapsedMillis: Long): Long = millis + if (since == null) 0L else maxOf(0L, elapsedMillis - since)
    }

    private val guard = Any()
    private val storage = Mutex()
    private val saved = repository.load()
    private var position: Position? = saved?.let { Position(it.simulatedMillis, null) }
    private var owner: String? = saved?.ownerUid
    private var run: String? = saved?.runItineraryId
    private var foreground = false

    private val _jumps = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val jumps: Flow<Unit> = _jumps.asSharedFlow()

    /** The account that turned demo mode on. */
    val ownerUid: String? get() = synchronized(guard) { owner }

    /** The Itinerary of the latest demo Start. */
    val runItineraryId: String? get() = synchronized(guard) { run }

    private fun simulatedMillis(): Long? = synchronized(guard) { position?.at(elapsed()) }

    override fun now(): Instant = simulatedMillis()?.let(Instant::ofEpochMilli) ?: real()

    override fun realNow(): Instant = real()

    override val isSimulated: Boolean get() = synchronized(guard) { position != null }

    override fun planningDate(): LocalDate = if (isSimulated) pinnedDate else now().atZone(zone).toLocalDate()

    override fun planningNow(): Instant =
        if (isSimulated) planningFrom.atStartOfDay(zone).toInstant() else now()

    /** The app came to the foreground: simulated time runs. */
    fun resume() {
        val simulated = synchronized(guard) {
            foreground = true
            val millis = simulatedMillis() ?: return@synchronized false
            position = Position(millis, elapsed())
            true
        }
        if (simulated) _jumps.tryEmit(Unit)
    }

    /** The app left the foreground: simulated time stops. Call [checkpoint] to save it. */
    fun pause() = synchronized(guard) {
        foreground = false
        val millis = simulatedMillis() ?: return@synchronized
        position = Position(millis, null)
    }

    /** Saves the current simulated time, so a killed process restarts from here. */
    suspend fun checkpoint() = storage.withLock {
        simulatedMillis()?.let { repository.save(DemoSnapshot(it, ownerUid, runItineraryId)) }
        Unit
    }

    /** Turns demo mode on for [ownerUid] at [instant]. */
    suspend fun open(instant: Instant, ownerUid: String) =
        storage.withLock { place(DemoSnapshot(instant.toEpochMilli(), ownerUid, null)) }

    /** Moves to [instant], even backward, for the demo run [runItineraryId]. Used by Start. */
    suspend fun moveTo(instant: Instant, runItineraryId: String) =
        storage.withLock { place(DemoSnapshot(instant.toEpochMilli(), ownerUid, runItineraryId)) }

    /** Moves forward to [instant]. Never moves the time backward. */
    suspend fun advanceTo(instant: Instant) = storage.withLock {
        val millis = maxOf(instant.toEpochMilli(), simulatedMillis() ?: Long.MIN_VALUE)
        place(DemoSnapshot(millis, ownerUid, runItineraryId))
    }

    private suspend fun place(snapshot: DemoSnapshot) {
        repository.save(snapshot)
        synchronized(guard) {
            position = Position(snapshot.simulatedMillis, if (foreground) elapsed() else null)
            owner = snapshot.ownerUid
            run = snapshot.runItineraryId
        }
        _jumps.tryEmit(Unit)
    }

    /** Back to real time. */
    suspend fun stop() = storage.withLock {
        repository.clear()
        synchronized(guard) {
            position = null
            owner = null
            run = null
        }
        _jumps.tryEmit(Unit)
        Unit
    }

    /** Where demo mode opens: the pinned date in the morning. */
    fun pinnedStart(): Instant = pinnedDate.atTime(PINNED_START).atZone(zone).toInstant()

    private companion object {
        val PINNED_START: LocalTime = LocalTime.of(10, 0)
    }
}

/** Without a known origin, the demo opens this long before the first Stop. */
private val DEMO_LEAD: Duration = Duration.ofMinutes(30)

/**
 * Where a demo run begins: the first Stop's Leave By from [origin] in the default Leg mode, or
 * [DEMO_LEAD] before it. Kept on the Itinerary's date and before its window ends, so the normal
 * Start rule would accept this time. Null when the Itinerary has no active Stop.
 */
fun demoStartInstant(
    itinerary: Itinerary,
    origin: LatLng?,
    originDistrictId: String?,
    estimator: LegEstimator,
    zone: ZoneId,
): Instant? {
    val first = itinerary.activeStops.minByOrNull { it.start.toInstant() } ?: return null
    val lead = origin?.let {
        Duration.ofMinutes(estimator.defaultEstimate(originDistrictId, first.districtId, it, first.latLng).totalMinutes.toLong())
    } ?: DEMO_LEAD
    val earliest = itinerary.date.atStartOfDay(zone).toInstant()
    val latest = itinerary.windowEnd(zone).toInstant().minusMillis(1)
    return minOf(maxOf(first.start.toInstant().minus(lead), earliest), latest)
}
