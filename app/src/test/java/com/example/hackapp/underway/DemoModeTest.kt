package com.example.hackapp.underway

import com.example.hackapp.data.model.LatLng
import com.example.hackapp.itinerary.InMemoryItineraryRepository
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.Nov28
import com.example.hackapp.itinerary.StopStatus
import com.example.hackapp.itinerary.Toronto
import com.example.hackapp.itinerary.UID
import com.example.hackapp.itinerary.at
import com.example.hackapp.itinerary.fixedLegs
import com.example.hackapp.itinerary.night
import com.example.hackapp.itinerary.stop
import com.example.hackapp.passport.InMemoryStampRepository
import com.example.hackapp.passport.PlaceStampIdentityResolver
import com.example.hackapp.passport.StopStampResult
import com.example.hackapp.underway.UnderwayEvent as E
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class DemoModeTest {
    private var real: Instant = at(LocalDate.of(2026, 11, 22), 9).toInstant()
    private val demoSettings = InMemoryDemoModeRepository()
    private val itineraries = InMemoryItineraryRepository()
    private val states = InMemoryUnderwayRepository()
    private val stamps = InMemoryStampRepository()
    private val scheduler = FakeUnderwayAlertScheduler()
    private val notifier = FakeUnderwayNotifier()

    private var clock = newClock()
    private var coordinator = newCoordinator()
    private var demo = DemoController(coordinator, clock)

    private fun newClock() = DemoClock(demoSettings, Toronto, Nov28, LocalDate.of(2026, 11, 21), real = { real })

    private fun newCoordinator() = UnderwayCoordinator(
        itineraries, states, stamps, { PlaceStampIdentityResolver({ null }) }, fixedLegs(20), clock, scheduler, notifier,
    )

    /** A new process: everything in memory is rebuilt from what was saved. */
    private fun restart() {
        clock = newClock()
        coordinator = newCoordinator()
        demo = DemoController(coordinator, clock)
    }

    private fun passTime(minutes: Long) {
        real = real.plus(Duration.ofMinutes(minutes))
    }

    private suspend fun started(id: String = "it-1", origin: LatLng? = null, confirm: Boolean = false) =
        demo.startFor(UID, id, origin, confirm) as UnderwayStartResult.Started

    private suspend fun send(event: E, id: String = "it-1") {
        val result = coordinator.dispatch(UID, id, event)
        assertEquals(null, result.rejection)
    }

    /** Next moment until the clock reaches [time]. */
    private suspend fun advanceTo(time: Instant) {
        repeat(30) {
            if (!clock.now().isBefore(time)) return
            assertTrue(demo.nextMoment(UID) is DemoAdvance.Moved)
        }
        error("Never reached $time")
    }

    private fun dec5Itinerary(id: String = "it-1"): Itinerary {
        val day = LocalDate.of(2026, 12, 5)
        return night(
            id = id,
            date = day,
            stops = listOf(stop("x", at(day, 19), at(day, 21))),
        )
    }

    @Test
    fun `enabling pins Saturday November 28 and the time keeps running`() = runBlocking {
        assertFalse(demo.enabled)
        assertEquals(DemoEnableResult.Enabled, demo.enable(UID))
        assertTrue(demo.enabled)
        assertEquals(at(Nov28, 10).toInstant(), clock.now())
        assertEquals(Nov28, clock.planningDate())
        passTime(7)
        assertEquals(at(Nov28, 10, 7).toInstant(), clock.now())
        assertEquals(real, clock.realNow())
    }

    @Test
    fun `planning time stays before every Event while simulating`() = runBlocking {
        demo.enable(UID)
        assertEquals(LocalDate.of(2026, 11, 21).atStartOfDay(Toronto).toInstant(), clock.planningNow())
        demo.disable()
        assertEquals(real, clock.planningNow())
    }

    @Test
    fun `an Itinerary of another date starts and the clock jumps to it`() = runBlocking {
        itineraries.saveItinerary(dec5Itinerary())
        demo.enable(UID)
        val result = started()
        assertEquals(UnderwayPhase.Leg, result.view.phase)
        assertFalse(result.askExactAlarm)
        assertEquals(at(LocalDate.of(2026, 12, 5), 18, 30).toInstant(), clock.now())
    }

    @Test
    fun `with an origin the clock jumps to the first Leave By`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        started(origin = LatLng(45.5, -73.6))
        assertEquals(t(18, 40), clock.now())
    }

    @Test
    fun `a demo run never schedules a real alarm`() = runBlocking {
        scheduler.exactAllowed = false
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        val result = started()
        assertFalse(result.askExactAlarm)
        send(E.ConfirmArrival())
        demo.nextMoment(UID)
        coordinator.onAppOpen(UID)
        coordinator.onBoot(UID)
        assertTrue(scheduler.alertsFor("it-1").isEmpty())
    }

    @Test
    fun `a full simulated day with a skip, a brief visit, End of day and Stamps`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        started()
        assertEquals(t(18, 30), clock.now())

        // Stop A: a full visit. Its Leave By alert shows on Next moment.
        advanceTo(t(19))
        send(E.ConfirmArrival("a"))
        repeat(10) {
            if (notifier.shown.none { it.kind == AlertKind.LeaveBy }) demo.nextMoment(UID)
        }
        assertNotNull(notifier.shown.firstOrNull { it.kind == AlertKind.LeaveBy })
        send(E.Leaving("a"))

        // Stop B: skipped.
        send(E.SkipStop("b"))

        // Stop C, after midnight: five minutes of an hour is too brief.
        advanceTo(t(0, 30, Nov29))
        send(E.ConfirmArrival("c"))
        passTime(5)
        send(E.EndItinerary())

        val end = coordinator.endOfDay(UID, "it-1")!!
        assertEquals(EndReason.Finished, end.reason)
        assertEquals(
            listOf(StopOutcome.Visited, StopOutcome.Skipped, StopOutcome.TooBrief),
            end.results.map { it.outcome },
        )
        assertTrue(end.results[0].stamp is StopStampResult.NewStamp)
        assertEquals(StopStampResult.NoStamp, end.results[1].stamp)
        assertEquals(StopStampResult.NoStamp, end.results[2].stamp)

        val passport = stamps.observeStamps(UID).first()
        assertEquals(listOf("a"), passport.map { it.stopId })
        assertEquals(Nov28, passport.single().date)
        assertEquals("Sorted by real time", real.toEpochMilli(), passport.single().awardedAtMillis)
        assertEquals(ItineraryPhase.Completed, itineraries.getItinerary(UID, "it-1")!!.phase)
        assertEquals(DemoAdvance.NothingUnderway, demo.nextMoment(UID))
    }

    @Test
    fun `Next moment alone reaches the automatic end after midnight`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        started()
        var last: DemoAdvance = DemoAdvance.NothingUnderway
        repeat(30) {
            val step = demo.nextMoment(UID)
            if (step is DemoAdvance.Moved) last = step
        }
        val moved = last as DemoAdvance.Moved
        assertTrue(moved.dayEnded)
        assertEquals(t(4, 0, Nov29), moved.at)
        val end = coordinator.endOfDay(UID, "it-1")!!
        assertEquals(EndReason.Automatic, end.reason)
        assertTrue(end.results.all { it.outcome == StopOutcome.Skipped })
    }

    @Test
    fun `a run past the automatic end still ends on Next moment`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        started()
        passTime(60 * 24)
        val moved = demo.nextMoment(UID) as DemoAdvance.Moved
        assertTrue(moved.dayEnded)
    }

    @Test
    fun `the clock and the run survive a restart`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        started()
        advanceTo(t(19))
        send(E.ConfirmArrival("a"))
        val before = clock.now()

        passTime(10)
        restart()

        assertTrue(demo.enabled)
        assertEquals(before.plus(Duration.ofMinutes(10)), clock.now())
        coordinator.onAppOpen(UID)
        assertEquals(UnderwayPhase.AtStop, coordinator.view(UID, true)!!.phase)
        assertTrue(demo.nextMoment(UID) is DemoAdvance.Moved)
        assertTrue(scheduler.alertsFor("it-1").isEmpty())
    }

    @Test
    fun `a wall clock moved backward holds the simulated time`() = runBlocking {
        demo.enable(UID)
        passTime(-120)
        assertEquals(at(Nov28, 10).toInstant(), clock.now())
    }

    @Test
    fun `starting over another run moves the clock only after confirmation`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        itineraries.saveItinerary(dec5Itinerary("it-2"))
        demo.enable(UID)
        started()
        send(E.ConfirmArrival("a"))
        val before = clock.now()

        val asked = demo.startFor(UID, "it-2", null)
        assertTrue(asked is UnderwayStartResult.NeedsConfirmation)
        assertEquals(before, clock.now())
        assertEquals(ItineraryPhase.Underway, itineraries.getItinerary(UID, "it-1")!!.phase)

        started("it-2", confirm = true)
        assertEquals(ItineraryPhase.Completed, itineraries.getItinerary(UID, "it-1")!!.phase)
        assertEquals(before.toEpochMilli(), states.get(UID, "it-1")!!.endedAtMillis)
        assertEquals(at(LocalDate.of(2026, 12, 5), 18, 30).toInstant(), clock.now())
    }

    @Test
    fun `disabling ends the run, keeps its Stamps and returns to real time`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        started()
        advanceTo(t(19))
        send(E.ConfirmArrival("a"))
        passTime(60)

        demo.disable()

        assertFalse(demo.enabled)
        assertEquals(real, clock.now())
        val itinerary = itineraries.getItinerary(UID, "it-1")!!
        assertEquals(ItineraryPhase.Completed, itinerary.phase)
        assertEquals(
            listOf(StopStatus.Confirmed, StopStatus.Skipped, StopStatus.Skipped),
            itinerary.stops.map { it.status },
        )
        assertEquals(EndReason.EndedEarly, states.get(UID, "it-1")!!.endReason)
        assertEquals(listOf("a"), stamps.observeStamps(UID).first().map { it.stopId })
        assertTrue(scheduler.alertsFor("it-1").isEmpty())

        restart()
        assertFalse(demo.enabled)
        coordinator.onAppOpen(UID)
        assertEquals(1, stamps.observeStamps(UID).first().size)
    }

    @Test
    fun `enabling is refused while a real Itinerary is Underway`() = runBlocking {
        real = t(18)
        itineraries.saveItinerary(threeStops())
        coordinator.start(UID, "it-1", null)
        val result = demo.enable(UID)
        assertTrue(result is DemoEnableResult.RealItineraryUnderway)
        assertFalse(demo.enabled)
        assertTrue(scheduler.alertsFor("it-1").isNotEmpty())
    }

    @Test
    fun `another account's real Itinerary keeps real time and real alarms`() = runBlocking {
        val day = LocalDate.of(2026, 11, 27)
        real = at(day, 18).toInstant()
        val other = night(
            id = "real",
            date = day,
            stops = listOf(stop("r", at(day, 19), at(day, 21))),
        ).copy(ownerUid = "u2")
        itineraries.saveItinerary(other)
        coordinator.start("u2", "real", null)

        // Simulated Nov 28 is past this Itinerary's automatic end. Real time is not.
        demo.enable(UID)
        coordinator.onAppOpen("u2")

        assertEquals(ItineraryPhase.Underway, itineraries.getItinerary("u2", "real")!!.phase)
        assertTrue(scheduler.alertsFor("real").isNotEmpty())
        demo.disable()
        assertEquals(ItineraryPhase.Underway, itineraries.getItinerary("u2", "real")!!.phase)
    }

    @Test
    fun `a demo run whose settings were lost ends on the next app open`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        started()
        demoSettings.clear()
        restart()
        coordinator.onAppOpen(UID)
        assertEquals(ItineraryPhase.Completed, itineraries.getItinerary(UID, "it-1")!!.phase)
    }

    @Test
    fun `status reports what the control can do`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        assertEquals(DemoStatus(false, null, false), demo.observe(UID).first())
        demo.enable(UID)
        assertEquals(DemoStatus(true, clock.now(), false), demo.observe(UID).first())
        started()
        assertEquals(DemoStatus(true, clock.now(), true), demo.observe(UID).first())
    }
}
