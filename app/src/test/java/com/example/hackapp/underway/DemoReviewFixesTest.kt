package com.example.hackapp.underway

import com.example.hackapp.itinerary.InMemoryItineraryRepository
import com.example.hackapp.itinerary.Nov28
import com.example.hackapp.itinerary.Toronto
import com.example.hackapp.itinerary.UID
import com.example.hackapp.itinerary.at
import com.example.hackapp.itinerary.fixedLegs
import com.example.hackapp.itinerary.night
import com.example.hackapp.itinerary.stop
import com.example.hackapp.passport.InMemoryStampRepository
import com.example.hackapp.passport.PlaceStampIdentityResolver
import com.example.hackapp.scaffold.screens.underway.minuteTickDelay
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DemoReviewFixesTest {
    private var real: Instant = at(LocalDate.of(2026, 11, 27), 18).toInstant()
    private var elapsed = 0L
    private val itineraries = InMemoryItineraryRepository()
    private val states = InMemoryUnderwayRepository()
    private val clock = DemoClock(
        InMemoryDemoModeRepository(), Toronto, Nov28, LocalDate.of(2026, 11, 21), real = { real }, elapsed = { elapsed },
    ).apply { resume() }
    private val coordinator = UnderwayCoordinator(
        itineraries, states, InMemoryStampRepository(), { PlaceStampIdentityResolver({ null }) }, fixedLegs(20),
        clock, FakeUnderwayAlertScheduler(), FakeUnderwayNotifier(),
    )

    @Test
    fun `a real Underway page draws real time while the clock is simulated`() = runBlocking {
        val day = LocalDate.of(2026, 11, 27)
        val other = night(id = "real", date = day, stops = listOf(stop("r", at(day, 19), at(day, 21)))).copy(ownerUid = "u2")
        itineraries.saveItinerary(other)
        coordinator.start("u2", "real", null)
        coordinator.enableDemo(UID)
        assertEquals(real, coordinator.displayNow("u2"))
        assertEquals(at(Nov28, 10).toInstant(), coordinator.displayNow(UID))
    }

    @Test
    fun `a demo run draws simulated time`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        coordinator.enableDemo(UID)
        coordinator.start(UID, "it-1", null, demoMode = true)
        assertEquals(t(18, 30), coordinator.displayNow(UID))
    }

    @Test
    fun `the ticker waits for the next minute`() {
        assertEquals(60_000L, minuteTickDelay(120_000L, null))
        assertEquals(1L, minuteTickDelay(119_999L, null))
        assertEquals(30_000L, minuteTickDelay(90_000L, 60_000L))
    }

    @Test
    fun `a paused clock never makes the ticker spin`() {
        assertEquals(60_000L, minuteTickDelay(119_999L, 119_999L))
    }

    @Test
    fun `simulation changes report only turning on and off`() = runBlocking {
        val seen = async(start = CoroutineStart.UNDISPATCHED) { clock.simulationChanges().take(3).toList() }
        clock.open(clock.pinnedStart(), UID)
        yield()
        clock.advanceTo(t(12))
        yield()
        clock.stop()
        assertEquals(listOf(false, true, false), seen.await())
    }

    @Test
    fun `Next moment never advances by only one second`() {
        // B starts at 19:30 and takes 20 minutes, so Leave By is 19:10 and the late warning 19:10:01.
        val itinerary = night(stops = listOf(stop("a", at(Nov28, 19), at(Nov28, 21)), stop("b", at(Nov28, 19, 30), at(Nov28, 23))))
        val ctx = UnderwayContext(itinerary, fixedLegs(20), Toronto)
        val state = ctx.run(UnderwayEngine.begin(itinerary, t(18), null)!!, t(19) to UnderwayEvent.ConfirmArrival())
        val first = UnderwayEngine.nextMomentAfter(ctx, state, t(19, 5))!!
        assertEquals(t(19, 10).plusSeconds(1), first)
        val next = UnderwayEngine.nextMomentAfter(ctx, state, t(19, 10))!!
        assertTrue(next.isAfter(t(19, 10).plusSeconds(1)))
        assertFalse(UnderwayEngine.nextMomentAfter(ctx, state, first)!!.isBefore(first))
    }
}
