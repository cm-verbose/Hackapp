package com.example.hackapp.underway

import android.content.Context
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.activeStops
import com.example.hackapp.itinerary.latLng
import com.example.hackapp.itinerary.transit
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.services.LegEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The simulated time [simulatedMillis] was set at the real time [realMillis]. */
data class DemoAnchor(val simulatedMillis: Long, val realMillis: Long) {
    /** A wall clock moved backward holds the time at the anchor. */
    fun at(real: Instant): Instant =
        Instant.ofEpochMilli(simulatedMillis + maxOf(0L, real.toEpochMilli() - realMillis))
}

/** Keeps demo mode across process death. [load] is synchronous because the clock needs it at once. */
interface DemoModeRepository {
    fun load(): DemoAnchor?
    suspend fun save(anchor: DemoAnchor)
    suspend fun clear()
}

class InMemoryDemoModeRepository(private var anchor: DemoAnchor? = null) : DemoModeRepository {
    override fun load() = anchor
    override suspend fun save(anchor: DemoAnchor) { this.anchor = anchor }
    override suspend fun clear() { anchor = null }
}

class SharedPreferencesDemoModeRepository(context: Context) : DemoModeRepository {
    private val prefs = context.applicationContext.getSharedPreferences("demo_mode", Context.MODE_PRIVATE)

    override fun load(): DemoAnchor? {
        if (!prefs.contains(KEY_SIMULATED) || !prefs.contains(KEY_REAL)) return null
        return DemoAnchor(prefs.getLong(KEY_SIMULATED, 0L), prefs.getLong(KEY_REAL, 0L))
    }

    override suspend fun save(anchor: DemoAnchor) = commit {
        putLong(KEY_SIMULATED, anchor.simulatedMillis).putLong(KEY_REAL, anchor.realMillis)
    }

    override suspend fun clear() = commit { remove(KEY_SIMULATED).remove(KEY_REAL) }

    private suspend fun commit(change: android.content.SharedPreferences.Editor.() -> Unit) =
        withContext(Dispatchers.IO) {
            val editor = prefs.edit()
            editor.change()
            if (!editor.commit()) throw IOException("Could not save demo mode")
        }

    private companion object {
        const val KEY_SIMULATED = "simulated_millis"
        const val KEY_REAL = "real_millis"
    }
}

/**
 * The app's one clock. Real time when demo mode is off. When on, it runs forward from a saved
 * anchor, so it survives process death. Each change is saved before it takes effect.
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
    @Volatile
    private var anchor: DemoAnchor? = repository.load()

    private val _jumps = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val jumps: Flow<Unit> = _jumps.asSharedFlow()

    override fun now(): Instant {
        val realNow = real()
        return anchor?.at(realNow) ?: realNow
    }

    override fun realNow(): Instant = real()

    override val isSimulated: Boolean get() = anchor != null

    override fun planningDate(): LocalDate = if (isSimulated) pinnedDate else now().atZone(zone).toLocalDate()

    override fun planningNow(): Instant =
        if (isSimulated) planningFrom.atStartOfDay(zone).toInstant() else now()

    /** Turns simulation on if needed and moves to [instant]. */
    suspend fun jumpTo(instant: Instant) {
        val next = DemoAnchor(instant.toEpochMilli(), real().toEpochMilli())
        repository.save(next)
        anchor = next
        _jumps.tryEmit(Unit)
    }

    /** Back to real time. */
    suspend fun stop() {
        repository.clear()
        anchor = null
        _jumps.tryEmit(Unit)
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
 * Where a demo run begins: the first Stop's Leave By from [origin], or [DEMO_LEAD] before it. Null
 * when the Itinerary has no active Stop.
 */
fun demoStartInstant(itinerary: Itinerary, origin: LatLng?, estimator: LegEstimator, zone: ZoneId): Instant? {
    val first = itinerary.activeStops.minByOrNull { it.start.toInstant() } ?: return null
    val start = first.start.atZoneSameInstant(zone).toInstant()
    return if (origin != null) {
        start.minus(Duration.ofMinutes(estimator.transit(origin, first.latLng).totalMinutes.toLong()))
    } else {
        start.minus(DEMO_LEAD)
    }
}
