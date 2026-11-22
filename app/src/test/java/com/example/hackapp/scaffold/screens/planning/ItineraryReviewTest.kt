package com.example.hackapp.scaffold.screens.planning

import com.example.hackapp.itinerary.BudgetPrices
import com.example.hackapp.itinerary.InMemoryItineraryRepository
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.MealBlock
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.itinerary.Nov28
import com.example.hackapp.itinerary.PlanningStep
import com.example.hackapp.itinerary.StartResult
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.itinerary.StopStatus
import com.example.hackapp.itinerary.Toronto
import com.example.hackapp.itinerary.UID
import com.example.hackapp.itinerary.at
import com.example.hackapp.itinerary.context
import com.example.hackapp.itinerary.event
import com.example.hackapp.itinerary.fixedLegs
import com.example.hackapp.itinerary.instant
import com.example.hackapp.itinerary.night
import com.example.hackapp.itinerary.startItinerary
import com.example.hackapp.itinerary.stop
import com.example.hackapp.services.LegEstimator
import com.example.hackapp.services.LegEstimate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ItineraryReviewTest {
    /** Stops about 0.015 degrees of latitude apart need an hour (a hub-and-spoke network); close ones 10 minutes. */
    private val hubLegs = LegEstimator { from, to, _ ->
        val minutes = if (kotlin.math.abs(from.lat - to.lat) > 0.015) 60 else 10
        LegEstimate(1000.0, minutes - 5, 5)
    }

    private fun at(stop: Stop, lat: Double) = stop.copy(latitude = lat)

    private val a = at(stop("a", at(Nov28, 18), at(Nov28, 18, 30), sourceId = "ev-a"), 45.50)
    private val b = at(stop("b", at(Nov28, 18, 40), at(Nov28, 19), sourceId = "ev-b"), 45.51)
    private val c = at(stop("c", at(Nov28, 19, 20), at(Nov28, 20), sourceId = "ev-c"), 45.52)
    private val d = at(stop("d", at(Nov28, 21), at(Nov28, 22), sourceId = "ev-d"), 45.52)

    private val inner = InMemoryItineraryRepository()
    private var failSaves = false
    private var failPrices = false
    private val repository = object : com.example.hackapp.itinerary.ItineraryRepository by inner {
        override suspend fun saveItinerary(itinerary: Itinerary) {
            if (failSaves) error("save failed")
            inner.saveItinerary(itinerary)
        }
    }
    private var now = instant(Nov28, 12)
    private var events = emptyList<com.example.hackapp.data.Event>()

    private fun holder(demoMode: Boolean = false, legs: LegEstimator = hubLegs) = ItineraryReviewHolder(
        itineraryId = "it-1",
        uid = { UID },
        itineraries = repository,
        estimator = legs,
        zone = Toronto,
        loadContext = { context(now = now) },
        loadPrices = { if (failPrices) error("prices failed") else BudgetPrices() },
        loadEvents = { events },
        // The rules the coordinator runs first, without its alerts and Underway state.
        starter = { uid, id, demo, confirmEndCurrent ->
            when (val result = startItinerary(repository, uid, id, now, Toronto, demo, confirmEndCurrent)) {
                is StartResult.Started -> ReviewStartOutcome.Started(result.itinerary.id, askExactAlarm = false)
                is StartResult.NeedsConfirmation -> ReviewStartOutcome.NeedsConfirmation(result.current)
                is StartResult.Blocked -> ReviewStartOutcome.Blocked
                StartResult.NotFound -> ReviewStartOutcome.NotFound
            }
        },
        demoMode = { demoMode },
        clock = { now },
    )

    private fun draft(vararg stops: Stop, step: PlanningStep = PlanningStep.Review) =
        night(stops = stops.toList()).copy(step = step)

    private fun showtime(id: String, hour: Int, minute: Int = 0) =
        event(id, start = at(Nov28, hour, minute).toString(), title = "Show", venue = "V", latitude = 45.52)

    private fun saved(): Itinerary = runBlocking { repository.getItinerary(UID, "it-1")!! }

    @Test
    fun `lists active Stops in order with numbers and Leave By`() = runBlocking {
        repository.saveItinerary(draft(a, b, c.copy(status = StopStatus.Skipped), d))
        val holder = holder()
        holder.load()
        val state = holder.state.value
        assertEquals(listOf("a", "b", "d"), state.stops.map { it.stop.id })
        assertEquals(listOf(1, 2, 3), state.stops.map { it.number })
        // a leaves 18:30 but must be at b by 18:40: the 10-minute Leg makes Leave By 18:30.
        assertEquals(at(Nov28, 18, 30), state.stops.first().leaveBy)
        assertTrue(state.budget != null)
        assertFalse(state.loading)
    }

    @Test
    fun `tapping a marker highlights its Stop, again clears it, unknown is ignored`() = runBlocking {
        repository.saveItinerary(draft(a, b))
        val holder = holder()
        holder.load()
        holder.selectStop("b")
        assertEquals("b", holder.state.value.highlightedStopId)
        assertEquals(listOf(false, true), holder.state.value.stops.map { it.highlighted })
        holder.selectStop("zzz")
        assertEquals("b", holder.state.value.highlightedStopId)
        holder.selectStop("a")
        assertEquals("a", holder.state.value.highlightedStopId)
        holder.selectStop("a")
        assertNull(holder.state.value.highlightedStopId)
    }

    @Test
    fun `a missing Itinerary is a load failure that retry can fix`() = runBlocking {
        val holder = holder()
        holder.load()
        assertTrue(holder.state.value.loadFailed)
        repository.saveItinerary(draft(a))
        holder.load()
        assertFalse(holder.state.value.loadFailed)
    }

    @Test
    fun `start state follows the date, the window and demo mode`() {
        val itinerary = draft(a)
        fun state(at: java.time.Instant, demo: Boolean = false) = reviewStartState(itinerary, at, Toronto, demo)
        assertEquals(ReviewStartState.NotToday(Nov28), state(instant(Nov28.minusDays(1), 12)))
        assertEquals(ReviewStartState.Ready(), state(instant(Nov28, 12)))
        // The hours after midnight still belong to the Itinerary's date.
        assertEquals(ReviewStartState.Ready(), state(instant(Nov28.plusDays(1), 1, 59)))
        assertEquals(ReviewStartState.WindowEnded(Nov28), state(instant(Nov28.plusDays(1), 2)))
        assertEquals(ReviewStartState.Ready(demoOverride = true), state(instant(Nov28.minusDays(3), 12), demo = true))
        assertEquals(ReviewStartState.Ready(), state(instant(Nov28, 12), demo = true))
        assertEquals(ReviewStartState.NoEvents, reviewStartState(draft(), instant(Nov28, 12), Toronto, false))
        assertEquals(
            ReviewStartState.NotPlanning,
            reviewStartState(itinerary.copy(phase = ItineraryPhase.Underway), instant(Nov28, 12), Toronto, false),
        )
    }

    @Test
    fun `start on another day does nothing and exposes the date`() = runBlocking {
        now = instant(Nov28.minusDays(2), 12)
        repository.saveItinerary(draft(a))
        val holder = holder()
        holder.load()
        assertEquals(ReviewStartState.NotToday(Nov28), holder.state.value.start)
        holder.start()
        assertEquals(ItineraryPhase.Planning, saved().phase)
        assertNull(holder.state.value.navigation)
    }

    @Test
    fun `demo mode starts on any date`() = runBlocking {
        now = instant(Nov28.minusDays(2), 12)
        repository.saveItinerary(draft(a))
        val holder = holder(demoMode = true)
        holder.load()
        holder.start()
        assertEquals(ItineraryPhase.Underway, saved().phase)
        assertEquals(ReviewNavigation.Started("it-1"), holder.state.value.navigation)
    }

    @Test
    fun `starting while another is Underway asks first and then moves the old one to Past`() = runBlocking {
        val confirmed = a.copy(status = StopStatus.Confirmed)
        val old = night(id = "old", phase = ItineraryPhase.Underway, stops = listOf(confirmed, b, c.copy(status = StopStatus.Skipped)))
        repository.saveItinerary(old)
        repository.saveItinerary(draft(d))
        val holder = holder()
        holder.load()
        holder.start()

        val dialog = holder.state.value.dialog as ReviewDialog.EndCurrent
        assertEquals(listOf("a"), dialog.confirmedStops.map { it.id })
        assertEquals(listOf("b"), dialog.skippedStops.map { it.id })
        assertEquals(ItineraryPhase.Underway, repository.getItinerary(UID, "old")!!.phase)
        assertEquals(ItineraryPhase.Planning, saved().phase)

        holder.confirmDialog()
        val past = repository.getItinerary(UID, "old")!!
        assertEquals(ItineraryPhase.Completed, past.phase)
        assertEquals(
            mapOf("a" to StopStatus.Confirmed, "b" to StopStatus.Skipped, "c" to StopStatus.Skipped),
            past.stops.associate { it.id to it.status },
        )
        assertEquals(ItineraryPhase.Underway, saved().phase)
        assertEquals(ReviewNavigation.Started("it-1"), holder.state.value.navigation)
        assertNull(holder.state.value.dialog)
    }

    @Test
    fun `cancelling the end-current warning changes nothing`() = runBlocking {
        repository.saveItinerary(night(id = "old", phase = ItineraryPhase.Underway, stops = listOf(a)))
        repository.saveItinerary(draft(d))
        val holder = holder()
        holder.load()
        holder.start()
        holder.dismissDialog()
        assertNull(holder.state.value.dialog)
        assertEquals(ItineraryPhase.Underway, repository.getItinerary(UID, "old")!!.phase)
        assertEquals(ItineraryPhase.Planning, saved().phase)
    }

    @Test
    fun `removing a Stop that affects nothing saves and returns to the gap`() = runBlocking {
        repository.saveItinerary(draft(a, b, d))
        val holder = holder(legs = fixedLegs(10))
        holder.load()
        holder.remove("b")
        assertNull(holder.state.value.dialog)
        assertEquals(listOf("a", "d"), saved().stops.map { it.id })
        assertEquals(PlanningStep.Stops, saved().step)
        assertEquals(ReviewNavigation.BackToSelection("it-1", "a", b.districtId), holder.state.value.navigation)
        assertEquals(listOf("a", "d"), holder.state.value.stops.map { it.stop.id })
    }

    @Test
    fun `removing the first Stop opens the gap at the start`() = runBlocking {
        repository.saveItinerary(draft(a, b))
        val holder = holder(legs = fixedLegs(10))
        holder.load()
        holder.remove("a")
        assertEquals(ReviewNavigation.BackToSelection("it-1", null, a.districtId), holder.state.value.navigation)
    }

    @Test
    fun `a later Stop that transit cannot reach is listed and nothing is saved`() = runBlocking {
        repository.saveItinerary(draft(a, b, c, d))
        val holder = holder()
        holder.load()
        holder.remove("b")
        val dialog = holder.state.value.dialog as ReviewDialog.RemoveAffects
        assertEquals("b", dialog.removed.id)
        // c needs an hour from a (leaves 18:30, starts 19:20). d is reachable in time and stays.
        assertEquals(listOf("c"), dialog.affected.map { it.id })
        assertFalse(dialog.canReschedule)
        assertEquals(listOf("a", "b", "c", "d"), saved().stops.map { it.id })
        assertNull(holder.state.value.navigation)
    }

    @Test
    fun `cancel keeps every Stop`() = runBlocking {
        repository.saveItinerary(draft(a, b, c))
        val holder = holder()
        holder.load()
        holder.remove("b")
        holder.dismissDialog()
        assertNull(holder.state.value.dialog)
        assertEquals(3, saved().stops.size)
        holder.reorganize()
        assertEquals(3, saved().stops.size)
    }

    @Test
    fun `reorganize drops the removed and affected Stops, keeps reachable ones, and goes to selection`() = runBlocking {
        repository.saveItinerary(draft(a, b, c, d))
        val holder = holder()
        holder.load()
        holder.remove("b")
        holder.reorganize()
        assertEquals(listOf("a", "d"), saved().stops.map { it.id })
        assertEquals(PlanningStep.Stops, saved().step)
        assertEquals(ReviewNavigation.BackToSelection("it-1", "a", b.districtId), holder.state.value.navigation)
        assertNull(holder.state.value.dialog)
    }

    @Test
    fun `reschedule offers another showtime of the same Event and saves after the last pick`() = runBlocking {
        events = listOf(showtime("ev-c", 19, 20), showtime("ev-c2", 20, 45), showtime("ev-c3", 19, 25))
        repository.saveItinerary(draft(a, b, c))
        val holder = holder()
        holder.load()
        holder.remove("b")
        val dialog = holder.state.value.dialog as ReviewDialog.RemoveAffects
        assertTrue(dialog.canReschedule)

        holder.reschedule()
        val step = holder.state.value.reschedule!!
        assertNull(holder.state.value.dialog)
        assertEquals("c", step.stop.id)
        assertEquals(1, step.total)
        // 19:25 is still too soon after the hour-long Leg from a; 20:45 works.
        assertEquals(listOf("ev-c2"), step.options.map { it.eventId })
        assertEquals(listOf("a", "b", "c"), saved().stops.map { it.id })

        holder.pickShowtime("ev-c2")
        assertNull(holder.state.value.reschedule)
        assertEquals(listOf("a", "event-ev-c2"), saved().stops.map { it.id })
        assertEquals(at(Nov28, 20, 45), saved().stops.last().start)
        assertEquals(PlanningStep.Review, saved().step)
        assertNull(holder.state.value.navigation)
    }

    @Test
    fun `reschedule is unavailable without another reachable showtime`() = runBlocking {
        events = listOf(showtime("ev-c", 19, 20), showtime("ev-c3", 19, 25))
        repository.saveItinerary(draft(a, b, c))
        val holder = holder()
        holder.load()
        holder.remove("b")
        assertFalse((holder.state.value.dialog as ReviewDialog.RemoveAffects).canReschedule)
        holder.reschedule()
        assertNull(holder.state.value.reschedule)
    }

    @Test
    fun `a Meal after the removed Stop is kept, its block margins cover the travel`() = runBlocking {
        val block = MealBlock("meal-dinner", MealKind.Dinner, LocalTime.of(19, 20))
        val meal = at(
            stop("m", at(Nov28, 19, 20), at(Nov28, 20, 50), kind = StopKind.Meal, sourceId = "5", mealBlockId = "meal-dinner"),
            45.52,
        )
        events = listOf(showtime("ev-c", 19, 20), showtime("ev-c2", 20, 45))
        repository.saveItinerary(draft(a, b, meal).copy(mealBlocks = listOf(block), planMeals = true))
        val holder = holder()
        holder.load()
        holder.remove("b")
        assertNull(holder.state.value.dialog)
        assertEquals(listOf("a", "m"), saved().stops.map { it.id })
    }

    @Test
    fun `cancelling the picker saves nothing and returns to the warning`() = runBlocking {
        events = listOf(showtime("ev-c", 19, 20), showtime("ev-c2", 20, 45))
        repository.saveItinerary(draft(a, b, c))
        val holder = holder()
        holder.load()
        holder.remove("b")
        holder.reschedule()
        holder.cancelReschedule()
        assertNull(holder.state.value.reschedule)
        assertTrue(holder.state.value.dialog is ReviewDialog.RemoveAffects)
        assertEquals(3, saved().stops.size)
    }

    @Test
    fun `reorganize also works from the picker`() = runBlocking {
        events = listOf(showtime("ev-c", 19, 20), showtime("ev-c2", 20, 45))
        repository.saveItinerary(draft(a, b, c, d))
        val holder = holder()
        holder.load()
        holder.remove("b")
        holder.reschedule()
        holder.reorganize()
        assertEquals(listOf("a", "d"), saved().stops.map { it.id })
        assertNull(holder.state.value.reschedule)
    }

    @Test
    fun `only a Planning Itinerary can have Stops removed`() = runBlocking {
        repository.saveItinerary(draft(a, b).copy(phase = ItineraryPhase.Underway))
        val holder = holder()
        holder.load()
        holder.remove("a")
        assertEquals(2, saved().stops.size)
    }

    @Test
    fun `a failed last reschedule save can be retried with the same pick`() = runBlocking {
        events = listOf(showtime("ev-c", 19, 20), showtime("ev-c2", 20, 45))
        repository.saveItinerary(draft(a, b, c))
        val holder = holder()
        holder.load()
        holder.remove("b")
        holder.reschedule()
        failSaves = true
        holder.pickShowtime("ev-c2")
        assertTrue(holder.state.value.actionFailed)
        assertFalse(holder.state.value.busy)
        assertEquals("c", holder.state.value.reschedule!!.stop.id)
        assertEquals(3, saved().stops.size)

        failSaves = false
        holder.pickShowtime("ev-c2")
        assertNull(holder.state.value.reschedule)
        assertEquals(listOf("a", "event-ev-c2"), saved().stops.map { it.id })
    }

    @Test
    fun `a price failure after a saved removal is not a save failure`() = runBlocking {
        repository.saveItinerary(draft(a, b, d))
        val holder = holder(legs = fixedLegs(10))
        holder.load()
        failPrices = true
        holder.remove("b")
        assertFalse(holder.state.value.actionFailed)
        assertTrue(holder.state.value.navigation is ReviewNavigation.BackToSelection)
        assertEquals(listOf("a", "d"), holder.state.value.stops.map { it.stop.id })
        assertTrue(holder.state.value.budget != null)
    }

    @Test
    fun `a failed Reorganize keeps the original canReschedule`() = runBlocking {
        events = listOf(showtime("ev-c", 19, 20), showtime("ev-c2", 20, 45))
        repository.saveItinerary(draft(a, b, c))
        val holder = holder()
        holder.load()
        holder.remove("b")
        failSaves = true
        holder.reorganize()
        assertTrue(holder.state.value.actionFailed)
        assertTrue((holder.state.value.dialog as ReviewDialog.RemoveAffects).canReschedule)
        assertEquals(3, saved().stops.size)
    }

    @Test
    fun `refreshing Start after midnight moves a ready Itinerary to window ended`() = runBlocking {
        repository.saveItinerary(draft(a))
        now = instant(Nov28, 23)
        val holder = holder()
        holder.load()
        assertEquals(ReviewStartState.Ready(), holder.state.value.start)
        now = instant(Nov28.plusDays(1), 2, 30)
        holder.refreshStart()
        assertEquals(ReviewStartState.WindowEnded(Nov28), holder.state.value.start)
    }

    @Test
    fun `cancelling the picker after a failed pick clears the save error`() = runBlocking {
        events = listOf(showtime("ev-c", 19, 20), showtime("ev-c2", 20, 45))
        repository.saveItinerary(draft(a, b, c))
        val holder = holder()
        holder.load()
        holder.remove("b")
        holder.reschedule()
        failSaves = true
        holder.pickShowtime("ev-c2")
        assertTrue(holder.state.value.actionFailed)
        holder.cancelReschedule()
        assertFalse(holder.state.value.actionFailed)
        assertTrue(holder.state.value.dialog is ReviewDialog.RemoveAffects)
    }

    @Test
    fun `the picker lists every Stop that Reorganize would remove`() = runBlocking {
        val c2 = at(stop("c2", at(Nov28, 19, 25), at(Nov28, 20), sourceId = "ev-d2"), 45.52)
        events = listOf(
            showtime("ev-c", 19, 20), showtime("ev-c2", 20, 45),
            event("ev-d2", start = at(Nov28, 19, 25).toString(), title = "Other", venue = "W", latitude = 45.52),
            event("ev-d3", start = at(Nov28, 22, 30).toString(), title = "Other", venue = "W", latitude = 45.52),
        )
        repository.saveItinerary(draft(a, b, c, c2))
        val holder = holder()
        holder.load()
        holder.remove("b")
        holder.reschedule()
        val first = holder.state.value.reschedule!!
        assertEquals(listOf("c", "c2"), first.affected.map { it.id })
        holder.pickShowtime("ev-c2")
        val second = holder.state.value.reschedule!!
        assertEquals("c2", second.stop.id)
        assertEquals(listOf("c", "c2"), second.affected.map { it.id })
    }
}
