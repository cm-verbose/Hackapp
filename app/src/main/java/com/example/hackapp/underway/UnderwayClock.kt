package com.example.hackapp.underway

import java.time.Instant
import java.time.ZoneId

/**
 * The only source of time for Underway. A demo clock can replace [SystemUnderwayClock] without
 * touching the engine, and can jump with [UnderwayEngine.nextMomentAfter].
 */
interface UnderwayClock {
    fun now(): Instant
    val zone: ZoneId
}

class SystemUnderwayClock(override val zone: ZoneId = ZoneId.systemDefault()) : UnderwayClock {
    override fun now(): Instant = Instant.now()
}
