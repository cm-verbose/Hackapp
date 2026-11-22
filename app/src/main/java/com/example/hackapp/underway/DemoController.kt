package com.example.hackapp.underway

import com.example.hackapp.data.model.LatLng
import com.example.hackapp.itinerary.Itinerary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.Instant

sealed interface DemoEnableResult {
    data object Enabled : DemoEnableResult
    /** A real Itinerary is Underway for this account. End it first. */
    data class RealItineraryUnderway(val current: Itinerary) : DemoEnableResult
}

sealed interface DemoAdvance {
    /** The clock is now [at]. [dayEnded] is true when this moment ended the Itinerary. */
    data class Moved(val at: Instant, val dayEnded: Boolean) : DemoAdvance
    data object NothingUnderway : DemoAdvance
    data object NotEnabled : DemoAdvance
}

/** What the demo control shows. [simulatedNow] is null when demo mode is off. */
data class DemoStatus(val enabled: Boolean, val simulatedNow: Instant?, val canAdvance: Boolean)

/** Turns demo mode on and off and moves through a demo run. See `docs/contracts/demo.md`. */
class DemoController(
    private val coordinator: UnderwayCoordinator,
    private val clock: DemoClock,
) {
    val enabled: Boolean get() = clock.isSimulated

    suspend fun enable(uid: String): DemoEnableResult = coordinator.enableDemo(uid)

    /** Ends unfinished demo runs, then returns to real time. Throws on a storage failure: retry. */
    suspend fun disable() = coordinator.disableDemo()

    /** Starts [itineraryId] whatever its date and jumps to it. Off demo mode, the normal rule applies. */
    suspend fun startFor(
        uid: String,
        itineraryId: String,
        origin: LatLng?,
        confirmEndCurrent: Boolean = false,
    ): UnderwayStartResult = coordinator.start(uid, itineraryId, origin, enabled, confirmEndCurrent)

    /** Jumps to the next meaningful time and fires what is due at once. */
    suspend fun nextMoment(uid: String): DemoAdvance = coordinator.advanceDemo(uid)

    /** Emits when demo mode, the time or the demo run changes. */
    fun observe(uid: String): Flow<DemoStatus> =
        combine(
            coordinator.observeDemoRun(uid),
            clock.jumps.onStart { emit(Unit) },
        ) { active, _ -> active }.map(::status)

    fun status(canAdvance: Boolean) = DemoStatus(
        enabled = enabled,
        simulatedNow = clock.now().takeIf { enabled },
        canAdvance = enabled && canAdvance,
    )
}
