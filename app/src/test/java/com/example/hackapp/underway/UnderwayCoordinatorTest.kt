package com.example.hackapp.underway

import com.example.hackapp.itinerary.InMemoryItineraryRepository
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.Nov28
import com.example.hackapp.itinerary.StartBlock
import com.example.hackapp.itinerary.UID
import com.example.hackapp.itinerary.at
import com.example.hackapp.itinerary.fixedLegs
import com.example.hackapp.itinerary.night
import com.example.hackapp.itinerary.stop
import com.example.hackapp.passport.InMemoryStampRepository
import com.example.hackapp.passport.PassportUnavailableException
import com.example.hackapp.passport.PlaceStampIdentityResolver
import com.example.hackapp.passport.StampAward
import com.example.hackapp.passport.StampIdentityResolver
import com.example.hackapp.passport.StampRepository
import com.example.hackapp.passport.StopStampResult
import com.example.hackapp.underway.UnderwayEvent as E
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.hackapp.itinerary.Itinerary as It

/** A Passport that fails the first [failures] awards. */
private class FlakyStamps(var failures: Int, val inner: StampRepository = InMemoryStampRepository()) : StampRepository by inner {
    var awardCalls = 0
    override suspend fun award(itinerary: Itinerary, resolver: StampIdentityResolver, awardedAtMillis: Long): StampAward {
        awardCalls++
        if (failures > 0) {
            failures--
            throw PassportUnavailableException(RuntimeException("disk"))
        }
        return inner.award(itinerary, resolver, awardedAtMillis)
    }
}

class UnderwayCoordinatorTest {
    private val clock = FakeClock(t(18))
    private val itineraries = InMemoryItineraryRepository()
    private val states = InMemoryUnderwayRepository()
    private val scheduler = FakeUnderwayAlertScheduler()
    private val notifier = FakeUnderwayNotifier()
    private val stamps = FlakyStamps(0)

    private fun coordinator(
        stampRepo: StampRepository = stamps,
        clk: UnderwayClock = clock,
        repo: UnderwayRepository = states,
        sched: UnderwayAlertScheduler = scheduler,
        its: com.example.hackapp.itinerary.ItineraryRepository = itineraries,
        resolver: suspend (It) -> StampIdentityResolver = { PlaceStampIdentityResolver({ null }) },
    ) = UnderwayCoordinator(
        its, repo, stampRepo, resolver, fixedLegs(20), clk, sched, notifier,
    )

    private fun draft(id: String = "it-1", stops: List<com.example.hackapp.itinerary.Stop> = listOf(stopA(), stopB(), stopC())): It =
        night(id = id, stops = stops)

    private fun save(it: It) = runBlocking { itineraries.saveItinerary(it) }

    private fun ev(c: UnderwayCoordinator, event: E, id: String = "it-1") =
        runBlocking { c.dispatch(UID, id, event) }

    @Test
    fun `start opens the first Leg and schedules its alert`() = runBlocking {
        save(draft())
        val c = coordinator()
        val r = c.start(UID, "it-1", null) as UnderwayStartResult.Started
        assertEquals(UnderwayPhase.Leg, r.view.phase)
        assertEquals(false, r.askExactAlarm)
        assertEquals(ItineraryPhase.Underway, itineraries.getItinerary(UID, "it-1")!!.phase)
        assertEquals(listOf(AlertKind.ArrivalReminder, AlertKind.AutoEnd), scheduler.alertsFor("it-1").map { it.kind })
    }

    @Test
    fun `denied exact alarms never block Start`() = runBlocking {
        save(draft())
        val sched = FakeUnderwayAlertScheduler(exactAllowed = false)
        val r = coordinator(sched = sched).start(UID, "it-1", null) as UnderwayStartResult.Started
        assertEquals(true, r.askExactAlarm)
        assertEquals(2, sched.alertsFor("it-1").size)
    }

    @Test
    fun `start only works on the Itinerary's date`() = runBlocking {
        save(draft())
        clock.current = t(18, day = Nov28.minusDays(1))
        val r = coordinator().start(UID, "it-1", null)
        assertEquals(UnderwayStartResult.Blocked(StartBlock.WrongDate), r)
    }

    @Test
    fun `after a process restart a new coordinator resumes from the stored state`() = runBlocking {
        save(draft())
        coordinator().start(UID, "it-1", null)
        ev(coordinator(), E.ConfirmArrival("a")).also { clock.current = t(19) }
        val reborn = coordinator()
        reborn.onAppOpen(UID)
        val view = reborn.view(UID, true)!!
        assertEquals(UnderwayPhase.AtStop, view.phase)
        assertEquals("a", view.currentStop!!.id)
        assertEquals(setOf(AlertKind.LeaveBy, AlertKind.LateWarning, AlertKind.AutoEnd), scheduler.alertsFor("it-1").map { it.kind }.toSet())
    }

    @Test
    fun `a missing state is recreated on app open`() = runBlocking {
        save(draft().copy(phase = ItineraryPhase.Underway))
        coordinator().onAppOpen(UID)
        assertEquals("a", coordinator().view(UID, true)!!.currentStop!!.id)
    }

    @Test
    fun `alarms notify once and notification actions update state`() = runBlocking {
        save(draft())
        val c = coordinator()
        c.start(UID, "it-1", null)
        clock.current = t(19, 30)
        c.onAlarm(UID, "it-1")
        c.onAlarm(UID, "it-1")
        assertEquals(1, notifier.shown.size)
        assertEquals(AlertKind.ArrivalReminder, notifier.shown.single().kind)
        val before = notifier.cancelCount
        val r = ev(c, E.ConfirmArrival(notifier.shown.single().stopId))
        assertEquals(UnderwayPhase.AtStop, r.view!!.phase)
        assertTrue(notifier.cancelCount > before)
        assertEquals(Rejection.StaleStop, ev(c, E.Leaving("zzz")).rejection)
    }

    private fun finishDay(c: UnderwayCoordinator) {
        ev(c, E.ConfirmArrival("a")); clock.current = t(20, 50)
        ev(c, E.Leaving("a")); clock.current = t(21, 30)
        ev(c, E.ConfirmArrival("b")); clock.current = t(22, 40)
        ev(c, E.Leaving("b")); clock.current = t(0, 30, Nov29)
        ev(c, E.ConfirmArrival("c")); clock.current = t(1, 20, Nov29)
        ev(c, E.EndItinerary())
    }

    @Test
    fun `End of day awards Stamps once and shows each Stop's result`() = runBlocking {
        save(draft())
        val c = coordinator()
        c.start(UID, "it-1", null)
        clock.current = t(19)
        finishDay(c)
        assertEquals(ItineraryPhase.Completed, itineraries.getItinerary(UID, "it-1")!!.phase)
        assertTrue(scheduler.alertsFor("it-1").isEmpty())
        val first = c.endOfDay(UID, "it-1")!!
        val again = c.endOfDay(UID, "it-1")!!
        assertEquals(first, again)
        assertEquals(listOf(StopOutcome.Visited, StopOutcome.Visited, StopOutcome.Visited), first.results.map { it.outcome })
        assertTrue(first.results.all { it.stamp is StopStampResult.NewStamp })
        assertEquals(3, stamps.inner.observeStamps(UID).first().size)
        assertTrue(states.get(UID, "it-1")!!.stampsAwarded)
    }

    @Test
    fun `a too brief Event is visited but earns no Stamp`() = runBlocking {
        save(draft())
        val c = coordinator()
        c.start(UID, "it-1", null)
        clock.current = t(19)
        ev(c, E.ConfirmArrival("a"))
        clock.current = t(19, 10)
        ev(c, E.EndItinerary(confirmed = true))
        val day = c.endOfDay(UID, "it-1")!!
        val a = day.results.first { it.stop.id == "a" }
        assertEquals(StopOutcome.TooBrief, a.outcome)
        assertEquals(StopStampResult.NoStamp, a.stamp)
        assertEquals(com.example.hackapp.itinerary.StopStatus.Confirmed, a.stop.status)
        assertEquals(0, stamps.inner.observeStamps(UID).first().size)
    }

    @Test
    fun `an unavailable Passport leaves End of day usable and retries later`() = runBlocking {
        save(draft())
        val flaky = FlakyStamps(failures = 2)
        val c = coordinator(stampRepo = flaky)
        c.start(UID, "it-1", null)
        clock.current = t(19)
        finishDay(c)
        assertEquals(false, states.get(UID, "it-1")!!.stampsAwarded)
        val offline = c.endOfDay(UID, "it-1")!!
        assertEquals(false, offline.stampsAvailable)
        assertEquals(3, offline.results.size)
        assertNull(offline.results.first().stamp)
        val recovered = c.endOfDay(UID, "it-1")!!
        assertEquals(true, recovered.stampsAvailable)
        assertTrue(recovered.results.all { it.stamp is StopStampResult.NewStamp })
        assertTrue(states.get(UID, "it-1")!!.stampsAwarded)
        val calls = flaky.awardCalls
        coordinator(stampRepo = flaky).onAppOpen(UID)
        assertEquals(calls, flaky.awardCalls)
        assertEquals(3, flaky.inner.observeStamps(UID).first().size)
    }

    @Test
    fun `app open and boot retry an unfinished award and an automatic ending`() = runBlocking {
        save(draft())
        val flaky = FlakyStamps(failures = 1)
        val c = coordinator(stampRepo = flaky)
        c.start(UID, "it-1", null)
        clock.current = t(19)
        ev(c, E.ConfirmArrival("a"))
        clock.current = t(20, 30)
        ev(c, E.SkipStop("b"))
        clock.current = t(9, day = Nov29) // long after the automatic end at 04:00
        c.onBoot(UID)
        val state = states.get(UID, "it-1")!!
        assertEquals(EndReason.Automatic, state.endReason)
        assertEquals(false, state.stampsAwarded) // the first award failed
        c.onAppOpen(UID)
        assertTrue(states.get(UID, "it-1")!!.stampsAwarded)
        assertEquals(1, flaky.inner.observeStamps(UID).first().size)
        assertEquals(ItineraryPhase.Completed, itineraries.getItinerary(UID, "it-1")!!.phase)
    }

    @Test
    fun `starting another Itinerary ends the current one and keeps its Stamps`() = runBlocking {
        save(draft())
        save(night(id = "it-2", stops = listOf(stop("z", at(Nov28, 22), at(Nov28, 23)))))
        val c = coordinator()
        c.start(UID, "it-1", null)
        clock.current = t(19)
        ev(c, E.ConfirmArrival("a"))
        clock.current = t(20, 30)
        ev(c, E.Leaving("a"))
        val ask = c.start(UID, "it-2", null)
        assertTrue(ask is UnderwayStartResult.NeedsConfirmation)
        assertEquals(ItineraryPhase.Underway, itineraries.getItinerary(UID, "it-1")!!.phase)
        val started = c.start(UID, "it-2", null, confirmEndCurrent = true)
        assertTrue(started is UnderwayStartResult.Started)
        val old = itineraries.getItinerary(UID, "it-1")!!
        assertEquals(ItineraryPhase.Completed, old.phase)
        assertEquals(EndReason.Replaced, states.get(UID, "it-1")!!.endReason)
        assertEquals(1, stamps.inner.observeStamps(UID).first().size)
        assertEquals("z", c.view(UID, true)!!.currentStop!!.id)
        assertNotNull(states.get(UID, "it-2"))
        assertTrue(scheduler.alertsFor("it-1").isEmpty())
    }

    @Test
    fun `a wake-up alarm is set at the automatic end`() = runBlocking {
        save(draft())
        coordinator().start(UID, "it-1", null)
        val autoEnd = scheduler.alertsFor("it-1").single { it.kind == AlertKind.AutoEnd }
        assertEquals(t(4, day = Nov29), autoEnd.at)
    }

    @Test
    fun `signing out cancels alarms and notifications`() = runBlocking {
        save(draft())
        val c = coordinator()
        c.start(UID, "it-1", null)
        val before = notifier.cancelCount
        c.onSignOut(UID)
        assertTrue(scheduler.alertsFor("it-1").isEmpty())
        assertTrue(notifier.cancelCount > before)
    }

    @Test
    fun `boot only reschedules the signed-in account`() = runBlocking {
        save(draft())
        val c = coordinator()
        c.start(UID, "it-1", null)
        scheduler.pending.clear()
        c.onBoot(null)
        c.onBoot("someone-else")
        assertTrue(scheduler.alertsFor("it-1").isEmpty())
        c.onBoot(UID)
        assertEquals(2, scheduler.alertsFor("it-1").size)
    }

    @Test
    fun `a failing Place lookup leaves the award to a later retry`() = runBlocking {
        save(draft())
        var broken = true
        val c = coordinator(resolver = { if (broken) error("places db") else PlaceStampIdentityResolver({ null }) })
        c.start(UID, "it-1", null)
        clock.current = t(19)
        finishDay(c)
        assertEquals(false, states.get(UID, "it-1")!!.stampsAwarded)
        assertEquals(false, c.endOfDay(UID, "it-1")!!.stampsAvailable)
        broken = false
        c.onAppOpen(UID)
        assertTrue(states.get(UID, "it-1")!!.stampsAwarded)
        assertEquals(3, stamps.inner.observeStamps(UID).first().size)
    }

    @Test
    fun `a failed Itinerary save still schedules alerts and clears notifications, then rethrows`() = runBlocking {
        save(draft())
        var failing = false
        val flaky = object : com.example.hackapp.itinerary.ItineraryRepository by itineraries {
            override suspend fun saveItinerary(itinerary: It) {
                if (failing) throw java.io.IOException("disk")
                itineraries.saveItinerary(itinerary)
            }
        }
        val c = coordinator(its = flaky)
        c.start(UID, "it-1", null)
        clock.current = t(19)
        failing = true
        val before = notifier.cancelCount
        val thrown = try { ev(c, E.ConfirmArrival("a")); null } catch (e: java.io.IOException) { e }
        assertNotNull(thrown)
        assertTrue(notifier.cancelCount > before)
        assertEquals(setOf(AlertKind.LeaveBy, AlertKind.LateWarning, AlertKind.AutoEnd), scheduler.alertsFor("it-1").map { it.kind }.toSet())
        // The state was saved first, so the next app open repairs the Itinerary.
        failing = false
        c.onAppOpen(UID)
        assertEquals(com.example.hackapp.itinerary.StopStatus.Confirmed, itineraries.getItinerary(UID, "it-1")!!.stops.first().status)
    }
}
