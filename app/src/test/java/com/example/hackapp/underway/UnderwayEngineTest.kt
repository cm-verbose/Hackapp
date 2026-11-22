package com.example.hackapp.underway

import com.example.hackapp.data.model.LatLng
import com.example.hackapp.itinerary.Nov28
import com.example.hackapp.itinerary.StopStatus
import com.example.hackapp.itinerary.at
import com.example.hackapp.itinerary.day
import com.example.hackapp.itinerary.night
import com.example.hackapp.itinerary.stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.hackapp.underway.UnderwayEvent as E

class UnderwayEngineTest {
    private val ctx = ctxOf()

    private fun reduce(s: UnderwayState, e: E, time: java.time.Instant) = UnderwayEngine.reduce(ctx, s, e, time)

    @Test
    fun `start opens the first Leg with transit and its Leave By`() {
        val s = begin(origin = LatLng(45.5, -73.6))
        assertEquals("a", s.currentStopId)
        assertEquals(TransportChoice.Transit, s.transport)
        val view = UnderwayEngine.view(ctx, s, t(18), true)
        assertEquals(at(Nov28, 18, 40), view.leaveBy)
        assertNull(UnderwayEngine.view(ctx, begin(), t(18), true).leaveBy)
    }

    @Test
    fun `opening a transport app goes to Arrival without assuming they left and can return`() {
        val s = begin()
        val arrival = reduce(s, E.OpenTransport(TransportChoice.Uber), t(18, 30)).state
        assertEquals(UnderwayPhase.Arrival, arrival.phase)
        assertEquals(TransportChoice.Uber, arrival.transport)
        val back = reduce(arrival, E.ReturnToLeg, t(18, 31)).state
        assertEquals(UnderwayPhase.Leg, back.phase)
        assertEquals("a", back.currentStopId)
    }

    @Test
    fun `later Legs start on the last transport choice and Leave By ignores it`() {
        val s = ctx.run(
            begin(),
            t(18, 30) to E.OpenTransport(TransportChoice.Uber),
            t(19) to E.ConfirmArrival("a"),
            t(20, 30) to E.Leaving("a"),
        )
        assertEquals("b", s.currentStopId)
        assertEquals(TransportChoice.Uber, s.transport)
        // Leave By from A: the earlier of departure 21:00 and B start 21:30 minus 20 min.
        assertEquals(at(Nov28, 21), UnderwayEngine.view(ctx, s, t(20, 31), true).leaveBy)
    }

    @Test
    fun `full day ends at the last Stop with no confirmation`() {
        val s = ctx.run(
            begin(),
            t(19) to E.ConfirmArrival(),
            t(20, 50) to E.Leaving(),
            t(21, 30) to E.ConfirmArrival(),
            t(22, 40) to E.Leaving(),
            t(0, 30, Nov29) to E.ConfirmArrival(),
        )
        val view = UnderwayEngine.view(ctx, s, t(0, 31, Nov29), true)
        assertTrue(view.isLastStop)
        assertFalse(view.endNeedsConfirmation)
        assertEquals(Rejection.WrongPhase, reduce(s, E.Leaving(), t(1, day = Nov29)).rejection)
        val ended = reduce(s, E.EndItinerary(), t(1, 25, Nov29))
        assertNull(ended.rejection)
        assertEquals(EndReason.Finished, ended.state.endReason)
        assertEquals(
            mapOf("a" to StopOutcome.Visited, "b" to StopOutcome.Visited, "c" to StopOutcome.Visited),
            ended.state.outcomes,
        )
    }

    @Test
    fun `skip works on Leg and Arrival and moves to the next Leg`() {
        val onLeg = reduce(begin(), E.SkipStop("a"), t(18, 10)).state
        assertEquals("b", onLeg.currentStopId)
        assertEquals(UnderwayPhase.Leg, onLeg.phase)
        val arrival = reduce(onLeg, E.OpenTransport(TransportChoice.Transit), t(18, 20)).state
        val skipped = reduce(arrival, E.SkipStop(), t(18, 30)).state
        assertEquals("c", skipped.currentStopId)
        assertEquals(UnderwayPhase.Leg, skipped.phase)
        val last = reduce(skipped, E.SkipStop(), t(18, 40)).state
        assertEquals(UnderwayPhase.Ended, last.phase)
        assertTrue(last.outcomes.values.all { it == StopOutcome.Skipped })
    }

    @Test
    fun `at a Stop only the next Stop can be skipped`() {
        val atA = ctx.run(begin(), t(19) to E.ConfirmArrival())
        assertEquals(Rejection.StaleStop, reduce(atA, E.SkipStop("a"), t(19, 5)).rejection)
        val skipped = reduce(atA, E.SkipStop("b"), t(19, 5)).state
        assertEquals(UnderwayPhase.AtStop, skipped.phase)
        val left = reduce(skipped, E.Leaving(), t(19, 10)).state
        assertEquals("c", left.currentStopId)
    }

    @Test
    fun `stale notification actions change nothing`() {
        val s = begin()
        val r = reduce(s, E.ConfirmArrival("b"), t(19))
        assertEquals(Rejection.StaleStop, r.rejection)
        assertEquals(s, r.state)
    }

    @Test
    fun `ending early needs confirmation then skips the remaining Stops`() {
        val atA = ctx.run(begin(), t(19) to E.ConfirmArrival())
        val ask = reduce(atA, E.EndItinerary(), t(19, 30))
        assertEquals(Rejection.NeedsConfirmation, ask.rejection)
        assertEquals(atA, ask.state)
        val ended = reduce(atA, E.EndItinerary(confirmed = true), t(19, 30)).state
        assertEquals(EndReason.EndedEarly, ended.endReason)
        assertEquals(StopOutcome.Visited, ended.outcomes["a"])
        assertEquals(StopOutcome.Skipped, ended.outcomes["b"])
        assertEquals(StopOutcome.Skipped, ended.outcomes["c"])
    }

    @Test
    fun `arriving late is recorded and replans nothing`() {
        val s = ctx.run(begin(), t(19, 45) to E.ConfirmArrival())
        assertEquals(t(19, 45).toEpochMilli(), s.records["a"]!!.arrivedAtMillis)
        assertEquals(listOf("a", "b", "c"), s.stopOrder)
    }

    @Test
    fun `an ended day is closed to every action`() {
        val ended = reduce(begin(), E.EndItinerary(confirmed = true), t(18, 30)).state
        assertEquals(Rejection.Ended, reduce(ended, E.ConfirmArrival(), t(19)).rejection)
        assertNull(reduce(ended, E.Tick, t(19)).rejection)
    }

    @Test
    fun `state survives a process restart through its stored form`() {
        val s = ctx.run(begin(), t(19) to E.ConfirmArrival(), t(19, 5) to E.Tick)
        val restored = UnderwayCodec.decode(UnderwayCodec.encode(s))!!
        assertEquals(s, restored)
        assertEquals(
            reduce(s, E.Leaving(), t(20)).state,
            reduce(restored, E.Leaving(), t(20)).state,
        )
    }

    // ---- Stamp rule ----

    private fun visitOutcome(arrive: java.time.Instant, leave: java.time.Instant?): StopOutcome {
        val itinerary = night(stops = listOf(stopA(), stopB()))
        val c = ctxOf(itinerary)
        val steps = mutableListOf<Pair<java.time.Instant, E>>(arrive to E.ConfirmArrival())
        if (leave != null) steps += leave to E.Leaving()
        val s = c.run(begin(itinerary), *steps.toTypedArray())
        val end = UnderwayEngine.reduce(c, s, E.EndItinerary(confirmed = true), maxOf(leave ?: arrive, t(21)))
        return end.state.outcomes.getValue("a")
    }

    @Test
    fun `any confirmed arrival is a visit, however short the stay`() {
        assertEquals(StopOutcome.Visited, visitOutcome(t(19), t(19, 1)))
        assertEquals(StopOutcome.Visited, visitOutcome(t(18), t(18, 59)))
        assertEquals(StopOutcome.Visited, visitOutcome(t(20, 59), t(22)))
    }

    @Test
    fun `a Meal needs only a confirmed arrival`() {
        val atB = ctx.run(
            begin(), t(19) to E.SkipStop("a"), t(21, 30) to E.ConfirmArrival("b"),
        )
        val ended = reduce(atB, E.EndItinerary(confirmed = true), t(21, 30)).state
        assertEquals(StopOutcome.Visited, ended.outcomes["b"])
        assertEquals(StopOutcome.Skipped, ended.outcomes["a"])
    }

    // ---- automatic end ----

    @Test
    fun `a forgotten Itinerary ends 2 hours after the window, after midnight`() {
        assertEquals(t(4, day = Nov29), UnderwayEngine.autoEndAt(ctx))
        val atC = ctx.run(begin(), t(19) to E.SkipStop("a"), t(21) to E.SkipStop("b"), t(0, 30, Nov29) to E.ConfirmArrival())
        val before = reduce(atC, E.Tick, t(3, 59, Nov29))
        assertEquals(UnderwayPhase.AtStop, before.state.phase)
        val late = reduce(atC, E.Tick, t(9, day = Nov29))
        assertEquals(UnderwayPhase.Ended, late.state.phase)
        assertEquals(EndReason.Automatic, late.state.endReason)
        assertEquals(t(4, day = Nov29).toEpochMilli(), late.state.endedAtMillis)
        // C 00:30-01:30: stay to the automatic end counts, capped at 01:30.
        assertEquals(StopOutcome.Visited, late.state.outcomes["c"])
        assertTrue(late.effects.contains(UnderwayEffect.DayEnded))
        assertEquals(Rejection.Ended, reduce(atC, E.Leaving(), t(9, day = Nov29)).rejection)
    }

    @Test
    fun `an action after the automatic end time finds the day already ended`() {
        val r = reduce(begin(), E.ConfirmArrival("a"), t(5, day = Nov29))
        assertEquals(Rejection.Ended, r.rejection)
        assertEquals(UnderwayPhase.Ended, r.state.phase)
        assertTrue(r.state.outcomes.values.all { it == StopOutcome.Skipped })
    }

    // ---- alerts ----

    @Test
    fun `the arrival reminder fires once, 30 minutes after the expected arrival`() {
        val s = begin()
        assertEquals(t(19, 30), UnderwayEngine.scheduledAlerts(ctx, s).single().at)
        assertTrue(reduce(s, E.Tick, t(19, 29)).effects.none { it is UnderwayEffect.Notify })
        val first = reduce(s, E.Tick, t(19, 30))
        assertEquals(1, first.effects.count { it is UnderwayEffect.Notify })
        val second = reduce(first.state, E.Tick, t(19, 45))
        assertEquals(0, second.effects.count { it is UnderwayEffect.Notify })
        assertTrue(UnderwayEngine.scheduledAlerts(ctx, first.state).isEmpty())
    }

    @Test
    fun `a Stop is never skipped automatically`() {
        val s = reduce(begin(), E.Tick, t(23)).state
        assertEquals("a", s.currentStopId)
        assertNull(s.records["a"]?.skippedAtMillis)
    }

    @Test
    fun `the leave alert fires 30 minutes before Leave By and never on the last Stop`() {
        val atA = ctx.run(begin(), t(19) to E.ConfirmArrival())
        val leave = UnderwayEngine.scheduledAlerts(ctx, atA).first { it.kind == AlertKind.LeaveBy }
        assertEquals(t(20, 30), leave.at)
        assertTrue(leave.highPriority)
        assertEquals("Stop b", leave.nextStopName)
        val atC = ctx.run(begin(), t(19) to E.SkipStop(), t(21) to E.SkipStop(), t(0, 30, Nov29) to E.ConfirmArrival())
        assertTrue(UnderwayEngine.scheduledAlerts(ctx, atC).none { it.kind == AlertKind.LeaveBy })
    }

    @Test
    fun `the late warning shows once per Stop and can be dismissed`() {
        val atA = ctx.run(begin(), t(19) to E.ConfirmArrival())
        // B starts 21:30 and the Leg takes 20 minutes: late after 21:10.
        assertNull(UnderwayEngine.view(ctx, atA, t(21, 10), true).lateWarningStop)
        assertEquals("b", UnderwayEngine.view(ctx, atA, t(21, 11), true).lateWarningStop?.id)
        val alert = UnderwayEngine.scheduledAlerts(ctx, atA).first { it.kind == AlertKind.LateWarning }
        assertEquals("b", alert.skipStopId)
        val fired = reduce(atA, E.Tick, t(21, 11)).state
        assertEquals(0, reduce(fired, E.Tick, t(21, 40)).effects.count { it is UnderwayEffect.Notify })
        val dismissed = reduce(atA, E.GoAnyway("b"), t(21, 12)).state
        assertNull(UnderwayEngine.view(ctx, dismissed, t(21, 15), true).lateWarningStop)
        assertTrue(UnderwayEngine.scheduledAlerts(ctx, dismissed).none { it.kind == AlertKind.LateWarning })
    }

    @Test
    fun `nextMomentAfter jumps to the next alert, Stop start or Leave By`() {
        val s = begin()
        assertEquals(t(19), UnderwayEngine.nextMomentAfter(ctx, s, t(18)))
        assertEquals(t(19, 30), UnderwayEngine.nextMomentAfter(ctx, s, t(19)))
        assertEquals(t(21, 30), UnderwayEngine.nextMomentAfter(ctx, s, t(19, 30)))
        val atA = ctx.run(s, t(19) to E.ConfirmArrival())
        assertEquals(t(20, 30), UnderwayEngine.nextMomentAfter(ctx, atA, t(19, 1)))
        assertEquals(t(21), UnderwayEngine.nextMomentAfter(ctx, atA, t(20, 30)))
        val ended = reduce(s, E.EndItinerary(true), t(19)).state
        assertNull(UnderwayEngine.nextMomentAfter(ctx, ended, t(19)))
    }

    @Test
    fun `the notifications banner shows once per Itinerary`() {
        val s = begin()
        assertTrue(UnderwayEngine.view(ctx, s, t(18), notificationsAllowed = false).showNotificationBanner)
        assertFalse(UnderwayEngine.view(ctx, s, t(18), notificationsAllowed = true).showNotificationBanner)
        val handled = reduce(s, E.NotificationBannerHandled, t(18, 1)).state
        assertFalse(UnderwayEngine.view(ctx, handled, t(18, 2), notificationsAllowed = false).showNotificationBanner)
    }

    @Test
    fun `syncing marks arrived Stops Confirmed and the rest Skipped when ended`() {
        val itinerary = threeStops()
        val s = ctx.run(begin(), t(19) to E.ConfirmArrival(), t(19, 10) to E.EndItinerary(true))
        val synced = UnderwayEngine.syncItinerary(itinerary, s, t(19, 10))
        assertEquals(listOf(StopStatus.Confirmed, StopStatus.Skipped, StopStatus.Skipped), synced.stops.map { it.status })
        assertEquals(com.example.hackapp.itinerary.ItineraryPhase.Completed, synced.phase)
        // Ten minutes is enough: every confirmed arrival earns its Stamp.
        assertEquals(StopOutcome.Visited, s.outcomes["a"])
        assertEquals(StopStatus.Confirmed, UnderwayEngine.stampItinerary(itinerary, s).stops.first().status)
        assertEquals(StopStatus.Confirmed, synced.stops.first().status)
    }

    @Test
    fun `skipping the next Stop gives the Leave By alert a new id so it alerts again`() {
        val atA = ctx.run(begin(), t(19) to E.ConfirmArrival())
        val first = UnderwayEngine.scheduledAlerts(ctx, atA).single { it.kind == AlertKind.LeaveBy }
        assertEquals("LeaveBy:a:b", first.id)
        val fired = reduce(atA, E.Tick, t(21)).state
        assertTrue(first.id in fired.firedAlerts)
        val skipped = reduce(fired, E.SkipStop("b"), t(21, 1)).state
        val second = UnderwayEngine.scheduledAlerts(ctx, skipped).single { it.kind == AlertKind.LeaveBy }
        assertEquals("LeaveBy:a:c", second.id)
    }

    @Test
    fun `the automatic end is an alarm and never a notification`() {
        val s = begin()
        val alarms = UnderwayEngine.alarmsToSchedule(ctx, s)
        assertEquals(t(4, day = Nov29), alarms.single { it.kind == AlertKind.AutoEnd }.at)
        assertTrue(reduce(s, E.Tick, t(23)).effects.none { it is UnderwayEffect.Notify && it.alert.kind == AlertKind.AutoEnd })
        val ended = reduce(s, E.EndItinerary(true), t(19)).state
        assertTrue(UnderwayEngine.alarmsToSchedule(ctx, ended).isEmpty())
    }

    @Test
    fun `a stale transport tap for another Stop changes nothing`() {
        val s = begin()
        val stale = reduce(s, E.OpenTransport(TransportChoice.Uber, "b"), t(18, 30))
        assertEquals(Rejection.StaleStop, stale.rejection)
        assertEquals(s, stale.state)
        val ok = reduce(s, E.OpenTransport(TransportChoice.Uber, "a"), t(18, 30))
        assertNull(ok.rejection)
        assertEquals(UnderwayPhase.Arrival, ok.state.phase)
    }

    @Test
    fun `leaving now moves the expected arrival and resets on the next Leg`() {
        val leg = ctx.run(begin(), t(19) to E.ConfirmArrival(), t(20, 40) to E.Leaving())
        val now = reduce(leg, E.ChooseDeparture(now = true, stopId = "b"), t(20, 41)).state
        // Leaves at 20:41 instead of the 21:00 Leave By, and the time no longer follows the clock.
        assertEquals(at(Nov28, 20, 51), UnderwayEngine.view(ctx, now, t(20, 45), true).expectedArrival)
        assertEquals(at(Nov28, 20, 41), UnderwayEngine.view(ctx, now, t(20, 45), true).departAt)
        val planned = reduce(now, E.ChooseDeparture(now = false, stopId = "b"), t(20, 46)).state
        assertEquals(at(Nov28, 21, 10), UnderwayEngine.view(ctx, planned, t(20, 46), true).expectedArrival)
        assertEquals(Rejection.StaleStop, reduce(leg, E.ChooseDeparture(true, "a"), t(20, 41)).rejection)
        val arrived = reduce(now, E.ConfirmArrival(), t(20, 52)).state
        assertNull(arrived.legDepartAtMillis)
    }

    @Test
    fun `expected arrival is the later of now and Leave By plus the travel time`() {
        val leg = ctx.run(begin(), t(19) to E.ConfirmArrival(), t(20, 50) to E.Leaving())
        // Leave By from A is 21:00 and the Leg travels 10 minutes (the other 10 are buffer).
        assertEquals(at(Nov28, 21, 10), UnderwayEngine.view(ctx, leg, t(20, 51), true).expectedArrival)
        assertEquals(at(Nov28, 21, 30), UnderwayEngine.view(ctx, leg, t(21, 20), true).expectedArrival)
        val arrival = reduce(leg, E.OpenTransport(TransportChoice.Uber, "b"), t(21, 20)).state
        assertEquals(at(Nov28, 21, 30), UnderwayEngine.view(ctx, arrival, t(21, 20), true).expectedArrival)
    }

    @Test
    fun `expected arrival is null with no previous Stop or at a Stop`() {
        val first = begin(origin = LatLng(45.5, -73.6))
        assertNull(UnderwayEngine.view(ctx, first, t(18), true).expectedArrival)
        val atA = ctx.run(first, t(19) to E.ConfirmArrival())
        assertNull(UnderwayEngine.view(ctx, atA, t(19, 5), true).expectedArrival)
    }

    @Test
    fun `alerts know when the Stop to skip is a Meal`() {
        val first = UnderwayEngine.scheduledAlerts(ctx, begin()).single()
        assertFalse(first.isMeal)
        val atA = ctx.run(begin(), t(19) to E.ConfirmArrival())
        val alerts = UnderwayEngine.scheduledAlerts(ctx, atA)
        assertTrue(alerts.single { it.kind == AlertKind.LeaveBy }.isMeal)
        assertTrue(alerts.single { it.kind == AlertKind.LateWarning }.isMeal)
        val atB = ctx.run(begin(), t(19) to E.ConfirmArrival(), t(20, 50) to E.Leaving(), t(21, 30) to E.ConfirmArrival())
        assertFalse(UnderwayEngine.scheduledAlerts(ctx, atB).single { it.kind == AlertKind.LeaveBy }.isMeal)
        val meal = UnderwayEngine.scheduledAlerts(ctx, ctx.run(begin(), t(19) to E.SkipStop())).single()
        assertTrue(meal.isMeal)
    }

    @Test
    fun `the last remaining Stop offers no Skip in its notification`() {
        // Skipping A then B leaves C as the only Stop.
        val onC = ctx.run(begin(), t(19) to E.SkipStop(), t(21) to E.SkipStop())
        assertNull(UnderwayEngine.scheduledAlerts(ctx, onC).single { it.kind == AlertKind.ArrivalReminder }.skipStopId)
        // With more Stops to come, Skip is offered.
        assertEquals("a", UnderwayEngine.scheduledAlerts(ctx, begin()).single().skipStopId)
    }

    @Test
    fun `a stale Skip from a notification cannot end the day`() {
        val onC = ctx.run(begin(), t(19) to E.SkipStop(), t(21) to E.SkipStop())
        val fromNotification = reduce(onC, E.SkipStop("c", fromNotification = true), t(22))
        assertEquals(Rejection.NeedsConfirmation, fromNotification.rejection)
        assertEquals(onC, fromNotification.state)
        // The in-app skip, with its own confirmation dialog, still works.
        assertEquals(UnderwayPhase.Ended, reduce(onC, E.SkipStop("c"), t(22)).state.phase)
        // A notification Skip is fine while Stops remain.
        assertEquals("b", reduce(begin(), E.SkipStop("a", fromNotification = true), t(19)).state.currentStopId)
    }

    @Test
    fun `on Arrival the expected arrival stays fixed at the handoff`() {
        val leg = ctx.run(begin(), t(19) to E.ConfirmArrival(), t(20, 50) to E.Leaving())
        val arrival = reduce(leg, E.OpenTransport(TransportChoice.Transit, "b"), t(21, 20)).state
        assertEquals(t(21, 20).toEpochMilli(), arrival.handoffAtMillis)
        // Handoff 21:20 is after Leave By 21:00: 21:20 + 10 minutes, whatever the time now.
        assertEquals(at(Nov28, 21, 30), UnderwayEngine.view(ctx, arrival, t(21, 20), true).expectedArrival)
        assertEquals(at(Nov28, 21, 30), UnderwayEngine.view(ctx, arrival, t(21, 45), true).expectedArrival)
        // The Leg itself still follows the clock.
        assertEquals(at(Nov28, 21, 55), UnderwayEngine.view(ctx, leg, t(21, 45), true).expectedArrival)
    }

    @Test
    fun `an early handoff waits for Leave By and a new handoff replaces the time`() {
        val leg = ctx.run(begin(), t(19) to E.ConfirmArrival(), t(20, 30) to E.Leaving())
        val arrival = reduce(leg, E.OpenTransport(TransportChoice.Transit), t(20, 40)).state
        assertEquals(at(Nov28, 21, 10), UnderwayEngine.view(ctx, arrival, t(21, 30), true).expectedArrival)
        val again = reduce(arrival, E.OpenTransport(TransportChoice.Uber), t(21, 15)).state
        assertEquals(t(21, 15).toEpochMilli(), again.handoffAtMillis)
        assertEquals(at(Nov28, 21, 25), UnderwayEngine.view(ctx, again, t(22), true).expectedArrival)
    }

    @Test
    fun `the handoff is cleared on return, arrival and skip, and older state still works`() {
        val leg = ctx.run(begin(), t(19) to E.ConfirmArrival(), t(20, 50) to E.Leaving())
        val arrival = reduce(leg, E.OpenTransport(TransportChoice.Transit), t(21)).state
        assertNull(reduce(arrival, E.ReturnToLeg, t(21, 1)).state.handoffAtMillis)
        assertNull(reduce(arrival, E.ConfirmArrival(), t(21, 2)).state.handoffAtMillis)
        assertNull(reduce(arrival, E.SkipStop(), t(21, 3)).state.handoffAtMillis)
        // A state saved before the field existed falls back to now.
        val legacy = arrival.copy(handoffAtMillis = null)
        assertEquals(at(Nov28, 21, 40), UnderwayEngine.view(ctx, legacy, t(21, 30), true).expectedArrival)
        val old = UnderwayCodec.encode(arrival).replace(Regex(",?\"handoffAtMillis\":\\d+"), "")
        assertNull(UnderwayCodec.decode(old)!!.handoffAtMillis)
    }

    // ---- walking inside a District ----

    /** Walking: 5 minutes of travel plus 5 of buffer. Transit: 20 plus 10. Ride hail: 8 plus 8. */
    private val modeLegs = object : com.example.hackapp.services.LegEstimator {
        override fun estimate(from: LatLng, to: LatLng, mode: com.example.hackapp.services.LegMode) = when (mode) {
            com.example.hackapp.services.LegMode.WALK -> com.example.hackapp.services.LegEstimate(400.0, 5, 5)
            com.example.hackapp.services.LegMode.TRANSIT -> com.example.hackapp.services.LegEstimate(400.0, 20, 10)
            com.example.hackapp.services.LegMode.RIDE_HAIL -> com.example.hackapp.services.LegEstimate(400.0, 8, 8)
        }
    }

    /** A in HERE 19:00-20:50, B in HERE 21:00-22:30, C in another District 22:30. */
    private fun walkItinerary() = night(
        stops = listOf(
            stop("a", at(Nov28, 19), at(Nov28, 20, 50)),
            stop("b", at(Nov28, 21), at(Nov28, 22, 30)),
            stop("c", at(Nov28, 22, 30), at(Nov28, 23), district = com.example.hackapp.itinerary.NEXT),
        ),
    )

    private fun walkCtx() = UnderwayContext(walkItinerary(), modeLegs, com.example.hackapp.itinerary.Toronto)

    @Test
    fun `a Leg inside a District walks by default and one across Districts does not`() {
        val c = walkCtx()
        val toB = c.run(begin(walkItinerary()), t(19) to E.ConfirmArrival(), t(19, 30) to E.Leaving())
        assertTrue(UnderwayEngine.view(c, toB, t(19, 31), true).walking)
        // Leave By is 21:00 minus the walk (5 + 5), and arrival adds the 5 minutes of walking.
        assertEquals(at(Nov28, 20, 50), UnderwayEngine.view(c, toB, t(19, 31), true).leaveBy)
        assertEquals(at(Nov28, 20, 55), UnderwayEngine.view(c, toB, t(19, 31), true).expectedArrival)
        val toC = c.run(toB, t(21) to E.ConfirmArrival(), t(21, 10) to E.Leaving())
        val view = UnderwayEngine.view(c, toC, t(21, 11), true)
        assertFalse(view.walking)
        // Transit: Leave By is 22:30 minus 30 minutes, then 20 minutes of travel.
        assertEquals(at(Nov28, 22), view.leaveBy)
        assertEquals(at(Nov28, 22, 20), view.expectedArrival)
    }

    @Test
    fun `picking transit or Uber uses its own time and ends the walking default for that Leg`() {
        val c = walkCtx()
        val toB = c.run(begin(walkItinerary()), t(19) to E.ConfirmArrival(), t(19, 30) to E.Leaving())
        val transit = reduce2(c, toB, E.OpenTransport(TransportChoice.Transit, "b"), t(20))
        val tv = UnderwayEngine.view(c, transit, t(20), true)
        assertFalse(tv.walking)
        assertEquals(at(Nov28, 20, 50).plusMinutes(20), tv.expectedArrival)
        val uber = reduce2(c, transit, E.OpenTransport(TransportChoice.Uber, "b"), t(20, 5))
        assertEquals(at(Nov28, 20, 50).plusMinutes(8), UnderwayEngine.view(c, uber, t(20, 5), true).expectedArrival)
        val back = reduce2(c, uber, E.ReturnToLeg, t(20, 6))
        assertFalse(UnderwayEngine.view(c, back, t(20, 6), true).walking)
        // The next Leg starts again on its own default: Uber is not carried onto a walk.
        val atB = reduce2(c, back, E.ConfirmArrival("b"), t(21))
        assertFalse(atB.legTransportChosen)
        val toC = reduce2(c, atB, E.Leaving(), t(21, 10))
        assertEquals(TransportChoice.Uber, UnderwayEngine.view(c, toC, t(21, 11), true).transport)
        assertFalse(UnderwayEngine.view(c, toC, t(21, 11), true).walking)
    }

    @Test
    fun `opening walking directions records a walk and keeps it walking`() {
        val c = walkCtx()
        val toB = c.run(begin(walkItinerary()), t(19) to E.ConfirmArrival(), t(19, 30) to E.Leaving())
        val walk = reduce2(c, toB, E.OpenTransport(TransportChoice.Walk, "b"), t(20))
        assertEquals(UnderwayPhase.Arrival, walk.phase)
        assertTrue(UnderwayEngine.view(c, walk, t(20), true).walking)
    }

    @Test
    fun `the late warning counts the walk, not transit`() {
        val c = walkCtx()
        val atB = c.run(begin(walkItinerary()), t(19) to E.ConfirmArrival(), t(19, 30) to E.Leaving(), t(21) to E.ConfirmArrival())
        // C starts 22:30 and the transit Leg takes 30 minutes: late after 22:00.
        assertNull(UnderwayEngine.view(c, atB, t(22), true).lateWarningStop)
        assertEquals("c", UnderwayEngine.view(c, atB, t(22, 1), true).lateWarningStop?.id)
        val atA = c.run(begin(walkItinerary()), t(19) to E.ConfirmArrival())
        // B starts 21:00 and the walk takes 10 minutes: late only after 20:50.
        assertNull(UnderwayEngine.view(c, atA, t(20, 50), true).lateWarningStop)
        assertEquals("b", UnderwayEngine.view(c, atA, t(20, 51), true).lateWarningStop?.id)
    }

    @Test
    fun `the first Leg from the District of stay walks when the stay District is the Stop's`() {
        val c = walkCtx()
        val here = UnderwayEngine.begin(walkItinerary(), t(18), LatLng(45.5, -73.6), originDistrictId = com.example.hackapp.itinerary.HERE)!!
        val view = UnderwayEngine.view(c, here, t(18), true)
        assertTrue(view.walking)
        assertEquals(at(Nov28, 18, 50), view.leaveBy)
        val other = UnderwayEngine.begin(walkItinerary(), t(18), LatLng(45.5, -73.6), originDistrictId = com.example.hackapp.itinerary.NEXT)!!
        val across = UnderwayEngine.view(c, other, t(18), true)
        assertFalse(across.walking)
        assertEquals(at(Nov28, 18, 30), across.leaveBy)
        // No known District or point: transit, and nothing to walk from.
        assertFalse(UnderwayEngine.view(c, begin(walkItinerary()), t(18), true).walking)
    }

    @Test
    fun `saved state from before walking still loads with the default`() {
        val old = UnderwayCodec.encode(begin())
            .replace(Regex(",?\"originDistrictId\":(null|\"[^\"]*\")"), "")
            .replace(Regex(",?\"legTransportChosen\":(true|false)"), "")
        val state = UnderwayCodec.decode(old)!!
        assertNull(state.originDistrictId)
        assertFalse(state.legTransportChosen)
    }

    private fun reduce2(c: UnderwayContext, s: UnderwayState, e: E, time: java.time.Instant): UnderwayState {
        val tr = UnderwayEngine.reduce(c, s, e, time)
        check(tr.rejection == null) { "$e rejected: ${tr.rejection}" }
        return tr.state
    }
}
