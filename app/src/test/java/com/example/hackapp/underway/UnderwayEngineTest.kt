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
    fun `an Event needs exactly 20 percent of its expected time`() {
        // 19:00-21:00 is 120 min, so 24 min is the boundary.
        assertEquals(StopOutcome.Visited, visitOutcome(t(19), t(19, 24)))
        assertEquals(StopOutcome.TooBrief, visitOutcome(t(19), t(19, 23)))
    }

    @Test
    fun `time before the start does not count`() {
        assertEquals(StopOutcome.Visited, visitOutcome(t(18, 30), t(19, 24)))
        assertEquals(StopOutcome.TooBrief, visitOutcome(t(18), t(19, 23)))
        assertEquals(StopOutcome.TooBrief, visitOutcome(t(18), t(18, 59)))
    }

    @Test
    fun `time after the Planned Departure does not count`() {
        assertEquals(StopOutcome.TooBrief, visitOutcome(t(20, 40), t(22)))
        assertEquals(StopOutcome.Visited, visitOutcome(t(20, 36), t(22)))
    }

    @Test
    fun `with no leaving tap the stay runs to the next action, capped at the expected end`() {
        val c = ctxOf(night(stops = listOf(stopA(), stopB())))
        fun outcome(arrive: java.time.Instant, next: java.time.Instant): StopOutcome {
            val atA = c.run(begin(night(stops = listOf(stopA(), stopB()))), arrive to E.ConfirmArrival())
            val after = UnderwayEngine.reduce(c, atA, E.SkipStop("b"), next).state
            return UnderwayEngine.reduce(c, after, E.EndItinerary(confirmed = true), next).state.outcomes.getValue("a")
        }
        assertEquals(StopOutcome.Visited, outcome(t(19), t(19, 30)))
        assertEquals(StopOutcome.TooBrief, outcome(t(19), t(19, 10)))
        // Next action at 23:00 is capped at 21:00: arriving 20:50 gives 10 of 120 minutes.
        assertEquals(StopOutcome.TooBrief, outcome(t(20, 50), t(23)))
        assertEquals(StopOutcome.Visited, outcome(t(19, 5), t(23)))
    }

    @Test
    fun `a drop-in Event counts from the arrival`() {
        val exhibition = stop("x", at(Nov28, 10), at(Nov28, 18))
        val itinerary = day(stops = listOf(exhibition, stop("y", at(Nov28, 18), at(Nov28, 19))))
        val c = ctxOf(itinerary)
        fun outcome(leave: java.time.Instant): StopOutcome {
            val s = c.run(begin(itinerary, t(9)), t(14) to E.ConfirmArrival(), leave to E.Leaving())
            return UnderwayEngine.reduce(c, s, E.EndItinerary(true), leave).state.outcomes.getValue("x")
        }
        // 14:00-18:00 is 240 min, so the boundary is 48 min.
        assertEquals(StopOutcome.Visited, outcome(t(14, 48)))
        assertEquals(StopOutcome.TooBrief, outcome(t(14, 47)))
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
        // 10 of 120 minutes: visited but too brief, so it earns no Stamp.
        assertEquals(StopOutcome.TooBrief, s.outcomes["a"])
        assertEquals(StopStatus.Skipped, UnderwayEngine.stampItinerary(itinerary, s).stops.first().status)
        assertEquals(StopStatus.Confirmed, synced.stops.first().status)
    }
}
