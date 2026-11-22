package com.example.hackapp.underway

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The only source of time for Underway. [DemoClock] replaces real time in demo mode and can jump
 * with [UnderwayEngine.nextMomentAfter].
 */
interface UnderwayClock {
    fun now(): Instant
    val zone: ZoneId

    /** Real time, even in demo mode. Real Itineraries and real alarms use only this. */
    fun realNow(): Instant = now()

    /** True in demo mode. */
    val isSimulated: Boolean get() = false

    /** The day a new draft starts from. */
    fun planningDate(): LocalDate = now().atZone(zone).toLocalDate()

    /** The time Planning compares Events with. */
    fun planningNow(): Instant = now()

    /** Emits when the time jumps, so pages can redraw at once. */
    val jumps: Flow<Unit> get() = emptyFlow()
}

class SystemUnderwayClock(override val zone: ZoneId = ZoneId.systemDefault()) : UnderwayClock {
    override fun now(): Instant = Instant.now()
}
