package com.example.hackapp.underway

import com.example.hackapp.data.model.LatLng
import com.example.hackapp.itinerary.InMemoryItineraryRepository
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.Nov28
import com.example.hackapp.itinerary.StartBlock
import com.example.hackapp.itinerary.StopStatus
import com.example.hackapp.itinerary.Toronto
import com.example.hackapp.itinerary.UID
import com.example.hackapp.itinerary.at
import com.example.hackapp.itinerary.fixedLegs
import com.example.hackapp.itinerary.night
import com.example.hackapp.itinerary.startBlock
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
    private var elapsed = 0L
    private val demoSettings = InMemoryDemoModeRepository()
    private val itineraries = InMemoryItineraryRepository()
    private val states = InMemoryUnderwayRepository()
    private val stamps = InMemoryStampRepository()
    private val scheduler = FakeUnderwayAlertScheduler()
    private val notifier = FakeUnderwayNotifier()

    private var clock = newClock()
    private var coordinator = newCoordinator()
    private var demo = DemoController(coordinator, clock)

    private fun newClock() =
        DemoClock(demoSettings, Toronto, Nov28, LocalDate.of(2026, 11, 21), real = { real }, elapsed = { elapsed }).apply { resume() }

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
        elapsed += minutes * 60_000
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
        demo.exit(UID)
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
    fun `the clock restarts paused at its saved time and the run survives`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        started()
        advanceTo(t(19))
        send(E.ConfirmArrival("a"))
        val arrived = states.get(UID, "it-1")!!.records["a"]!!.arrivedAtMillis!!

        passTime(600)
        clock = DemoClock(demoSettings, Toronto, Nov28, LocalDate.of(2026, 11, 21), real = { real }, elapsed = { elapsed })
        coordinator = newCoordinator()
        demo = DemoController(coordinator, clock)

        assertTrue(demo.enabled)
        assertTrue("Never before a recorded time", clock.now().toEpochMilli() >= arrived)
        assertEquals(t(19), clock.now())
        passTime(600)
        assertEquals("Paused until the app opens", t(19), clock.now())
        clock.resume()
        passTime(1)
        assertEquals(t(19, 1), clock.now())
        coordinator.onAppOpen(UID)
        assertEquals(UnderwayPhase.AtStop, coordinator.view(UID, true)!!.phase)
        assertTrue(scheduler.alertsFor("it-1").isEmpty())
    }

    @Test
    fun `a demo left overnight in the background does not end`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        started()
        val before = clock.now()
        clock.pause()
        clock.checkpoint()
        passTime(60 * 24 * 3)
        coordinator.onAlarm(UID, "it-1")
        coordinator.onBoot(UID)
        clock.resume()
        coordinator.onAppOpen(UID)
        assertEquals(before, clock.now())
        assertEquals(ItineraryPhase.Underway, itineraries.getItinerary(UID, "it-1")!!.phase)
    }

    @Test
    fun `Next moment never moves backward and visits Leave By, arrival and the next Stop`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        started()
        advanceTo(t(19))
        send(E.ConfirmArrival("a"))
        val seen = mutableListOf(clock.now())
        repeat(3) {
            demo.nextMoment(UID)
            seen += clock.now()
        }
        assertEquals(seen.sorted(), seen)
        assertEquals(seen.distinct(), seen)
        assertTrue("Leave By", t(21) in seen || t(21, 10) in seen)
        send(E.Leaving("a"))
        send(E.OpenTransport(TransportChoice.Transit, "b"))
        val handoff = clock.now()
        val expected = coordinator.view(UID, true)!!.expectedArrival!!.toInstant()
        demo.nextMoment(UID)
        assertTrue(clock.now() > handoff)
        assertTrue(clock.now() <= expected)
    }

    @Test
    fun `the start time is one the Itinerary window accepts`() {
        val dawn = night(
            stops = listOf(stop("d", at(Nov28, 0, 10), at(Nov28, 1))),
            start = java.time.LocalTime.of(0, 5),
            end = java.time.LocalTime.of(3, 0),
        )
        val at = demoStartInstant(dawn, LatLng(45.5, -73.6), null, fixedLegs(20), Toronto)!!
        assertEquals("Stays on its date", Nov28.atStartOfDay(Toronto).toInstant(), at)
        assertEquals(null, startBlock(dawn, at, Toronto))
        val evening = threeStops()
        assertEquals(null, startBlock(evening, demoStartInstant(evening, null, null, fixedLegs(20), Toronto)!!, Toronto))
    }

    @Test
    fun `the first Leg uses the default walking mode inside a District`() {
        val legs = com.example.hackapp.services.LegEstimator { _, _, mode ->
            val minutes = if (mode == com.example.hackapp.services.LegMode.WALK) 5 else 30
            com.example.hackapp.services.LegEstimate(distanceMeters = 300.0, travelMinutes = minutes, bufferMinutes = 5)
        }
        val itinerary = threeStops()
        val first = itinerary.stops.first()
        val at = demoStartInstant(itinerary, LatLng(45.5, -73.6), first.districtId, legs, Toronto)!!
        assertEquals(t(18, 50), at)
    }

    @Test
    fun `enabling reopens archived drafts and exiting archives them again`() = runBlocking {
        real = t(12, 0, Nov28.plusDays(5))
        itineraries.saveItinerary(threeStops().copy(phase = ItineraryPhase.Archived))
        demo.enable(UID)
        assertEquals(ItineraryPhase.Planning, itineraries.getItinerary(UID, "it-1")!!.phase)
        demo.exit(UID)
        assertEquals(ItineraryPhase.Archived, itineraries.getItinerary(UID, "it-1")!!.phase)
    }

    @Test
    fun `a wall clock change never moves the simulated time`() = runBlocking {
        demo.enable(UID)
        real = real.minus(Duration.ofHours(2))
        assertEquals(at(Nov28, 10).toInstant(), clock.now())
        real = real.plus(Duration.ofDays(3))
        assertEquals(at(Nov28, 10).toInstant(), clock.now())
        elapsed += 60_000
        assertEquals(at(Nov28, 10, 1).toInstant(), clock.now())
    }

    @Test
    fun `signing out exits demo mode, ends the run and keeps its Stamps`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        started()
        advanceTo(t(19))
        send(E.ConfirmArrival("a"))
        passTime(60)

        coordinator.onSignOut(UID)

        assertFalse(demo.enabled)
        assertEquals(ItineraryPhase.Completed, itineraries.getItinerary(UID, "it-1")!!.phase)
        assertEquals(listOf("a"), stamps.observeStamps(UID).first().map { it.stopId })
    }

    @Test
    fun `a sign-out that never finished cannot carry demo mode to the next account`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        started()

        coordinator.onAppOpen("u2")

        assertFalse(demo.enabled)
        assertEquals(ItineraryPhase.Completed, itineraries.getItinerary(UID, "it-1")!!.phase)
    }

    @Test
    fun `another account cannot start in the owner demo mode`() = runBlocking {
        val day = LocalDate.of(2026, 12, 5)
        itineraries.saveItinerary(night(id = "theirs", date = day, stops = listOf(stop("x", at(day, 19), at(day, 21)))).copy(ownerUid = "u2"))
        demo.enable(UID)
        val before = clock.now()
        val result = coordinator.start("u2", "theirs", null, demoMode = true)
        assertEquals(UnderwayStartResult.Blocked(StartBlock.WrongDate), result)
        assertEquals(before, clock.now())
    }

    @Test
    fun `a real run is refused even when demo mode is already on`() = runBlocking {
        val day = LocalDate.of(2026, 11, 22)
        itineraries.saveItinerary(night(id = "real", date = day, stops = listOf(stop("r", at(day, 19), at(day, 21)))).copy(phase = ItineraryPhase.Underway))
        demoSettings.save(DemoSnapshot(t(10).toEpochMilli(), UID))
        restart()
        assertTrue(demo.enable(UID) is DemoEnableResult.RealItineraryUnderway)
    }

    @Test
    fun `a demo run already Underway does not refuse enabling`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        started()
        assertEquals(DemoEnableResult.Enabled, demo.enable(UID))
    }

    @Test
    fun `a real run with missing state is never marked as a demo run`() = runBlocking {
        val day = LocalDate.of(2026, 11, 22)
        itineraries.saveItinerary(night(id = "real", date = day, stops = listOf(stop("r", at(day, 19), at(day, 21)))).copy(phase = ItineraryPhase.Underway))
        demoSettings.save(DemoSnapshot(t(10).toEpochMilli(), UID))
        restart()
        coordinator.onAppOpen(UID)
        assertFalse(states.get(UID, "real")!!.demoRun)
        assertTrue(scheduler.alertsFor("real").isNotEmpty())
    }

    @Test
    fun `a demo run that lost its state is rebuilt as a demo run`() = runBlocking {
        itineraries.saveItinerary(threeStops().copy(phase = ItineraryPhase.Underway))
        demoSettings.save(DemoSnapshot(t(18).toEpochMilli(), UID, "it-1"))
        restart()
        coordinator.onAppOpen(UID)
        assertTrue(states.get(UID, "it-1")!!.demoRun)
        assertTrue(scheduler.alertsFor("it-1").isEmpty())
    }

    @Test
    fun `retrying a partial exit finishes an Itinerary left Underway`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        started()
        val ended = states.get(UID, "it-1")!!.let {
            UnderwayEngine.reduce(ctxOf(itineraries.getItinerary(UID, "it-1")!!), it, E.EndItinerary(confirmed = true), clock.now()).state
        }
        states.save(ended)
        assertEquals(ItineraryPhase.Underway, itineraries.getItinerary(UID, "it-1")!!.phase)

        demo.exit(UID)

        assertEquals(ItineraryPhase.Completed, itineraries.getItinerary(UID, "it-1")!!.phase)
        assertFalse(demo.enabled)
    }

    @Test
    fun `an orphan run never ends before its latest recorded time`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        started()
        advanceTo(t(19))
        send(E.ConfirmArrival("a"))
        val arrived = states.get(UID, "it-1")!!.records["a"]!!.arrivedAtMillis!!
        demoSettings.clear()
        restart()
        coordinator.onAppOpen(UID)
        assertEquals(arrived, states.get(UID, "it-1")!!.endedAtMillis)
    }

    @Test
    fun `a demo run saves real time as the last change`() = runBlocking {
        itineraries.saveItinerary(threeStops())
        demo.enable(UID)
        started()
        assertEquals(real.toEpochMilli(), itineraries.getItinerary(UID, "it-1")!!.updatedAtMillis)
        passTime(5)
        send(E.ConfirmArrival("a"))
        assertEquals(real.toEpochMilli(), itineraries.getItinerary(UID, "it-1")!!.updatedAtMillis)
        passTime(5)
        send(E.EndItinerary(confirmed = true))
        assertEquals(real.toEpochMilli(), itineraries.getItinerary(UID, "it-1")!!.updatedAtMillis)
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

        demo.exit(UID)

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
        coordinator.onAlarm("u2", "real")

        assertEquals(ItineraryPhase.Underway, itineraries.getItinerary("u2", "real")!!.phase)
        assertTrue(scheduler.alertsFor("real").isNotEmpty())
        // When that account opens the app, demo mode ends and its run is untouched.
        coordinator.onAppOpen("u2")
        assertFalse(demo.enabled)
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
