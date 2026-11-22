package com.example.hackapp.underway

import android.content.Context
import android.content.SharedPreferences
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

/** Keeps the simulated time across process death. [load] is synchronous: the clock needs it at once. */
interface DemoModeRepository {
    fun load(): Long?
    suspend fun save(simulatedMillis: Long)
    suspend fun clear()
}

class InMemoryDemoModeRepository(private var simulatedMillis: Long? = null) : DemoModeRepository {
    override fun load() = simulatedMillis
    override suspend fun save(simulatedMillis: Long) { this.simulatedMillis = simulatedMillis }
    override suspend fun clear() { simulatedMillis = null }
}

class SharedPreferencesDemoModeRepository(context: Context) : DemoModeRepository {
    private val prefs = context.applicationContext.getSharedPreferences("demo_mode", Context.MODE_PRIVATE)

    override fun load(): Long? =
        if (prefs.contains(KEY_SIMULATED)) prefs.getLong(KEY_SIMULATED, 0L) else null

    override suspend fun save(simulatedMillis: Long) = commit { putLong(KEY_SIMULATED, simulatedMillis) }

    override suspend fun clear() = commit { remove(KEY_SIMULATED) }

    private suspend fun commit(change: SharedPreferences.Editor.() -> Unit) = withContext(Dispatchers.IO) {
        val editor = prefs.edit()
        editor.change()
        if (!editor.commit()) throw IOException("Could not save demo mode")
    }

    private companion object {
        const val KEY_SIMULATED = "simulated_millis"
    }
}

/**
 * The app's one clock. Real time when demo mode is off. When on, simulated time runs only while
 * the app is in the foreground ([resume], [pause]) and restarts paused at its saved time.
 */
class DemoClock(
    private val repository: DemoModeRepository,
    override val zone: ZoneId,
    /** The date a new draft defaults to in demo mode. */
    val pinnedDate: LocalDate,
    /** The first day with Events: nothing reads as past while planning in demo mode. */
    private val planningFrom: LocalDate,
    private val real: () -> Instant = Instant::now,
) : UnderwayClock {
    /** Simulated [millis] at the real time [since]. A null [since] means paused. */
    private class Position(val millis: Long, val since: Long?) {
        fun at(realMillis: Long): Long = millis + if (since == null) 0L else maxOf(0L, realMillis - since)
    }

    private val guard = Any()
    private val storage = Mutex()
    private var position: Position? = repository.load()?.let { Position(it, null) }
    private var foreground = false

    /** The latest time returned, so a wall clock moved backward never moves the time back. */
    private var latest = Long.MIN_VALUE

    private val _jumps = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val jumps: Flow<Unit> = _jumps.asSharedFlow()

    private fun simulatedMillis(): Long? = synchronized(guard) {
        val current = position ?: return null
        latest = maxOf(latest, current.at(real().toEpochMilli()))
        latest
    }

    override fun now(): Instant = simulatedMillis()?.let(Instant::ofEpochMilli) ?: real()

    override fun realNow(): Instant = real()

    override val isSimulated: Boolean get() = synchronized(guard) { position != null }

    override fun planningDate(): LocalDate = if (isSimulated) pinnedDate else now().atZone(zone).toLocalDate()

    override fun planningNow(): Instant =
        if (isSimulated) planningFrom.atStartOfDay(zone).toInstant() else now()

    /** The app came to the foreground: simulated time runs. */
    fun resume() = synchronized(guard) {
        foreground = true
        val millis = simulatedMillis() ?: return@synchronized
        position = Position(millis, real().toEpochMilli())
    }

    /** The app left the foreground: simulated time stops. Call [checkpoint] to save it. */
    fun pause() = synchronized(guard) {
        foreground = false
        val millis = simulatedMillis() ?: return@synchronized
        position = Position(millis, null)
    }

    /** Saves the current simulated time, so a killed process restarts from here. */
    suspend fun checkpoint() = storage.withLock {
        simulatedMillis()?.let { repository.save(it) }
        Unit
    }

    /** Turns simulation on if needed and moves to [instant], even backward. Used by Start. */
    suspend fun moveTo(instant: Instant) = storage.withLock { place(instant.toEpochMilli()) }

    /** Moves forward to [instant]. Never moves the time backward. */
    suspend fun advanceTo(instant: Instant) = storage.withLock {
        place(maxOf(instant.toEpochMilli(), simulatedMillis() ?: Long.MIN_VALUE))
    }

    private suspend fun place(millis: Long) {
        repository.save(millis)
        synchronized(guard) {
            position = Position(millis, if (foreground) real().toEpochMilli() else null)
            latest = millis
        }
        _jumps.tryEmit(Unit)
    }

    /** Back to real time. */
    suspend fun stop() = storage.withLock {
        repository.clear()
        synchronized(guard) {
            position = null
            latest = Long.MIN_VALUE
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
