package com.example.hackapp.itinerary

import com.example.hackapp.scaffold.screens.planning.withMealSkipped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class StopSelectionTest {
    private val legs = fixedLegs()
    private val dinner = MealBlock("d", MealKind.Dinner, LocalTime.of(20, 0))
    private val now = instant(Nov28, 8)

    private fun found(itinerary: Itinerary, until: Boolean = true, vararg events: com.example.hackapp.data.Event): List<ReachableShowtime> {
        val gapEnd = itinerary.pendingMealBlock(now, Toronto)?.interval(itinerary, Toronto)?.start
        return reachableShowtimes(
            EventSearch(itinerary, events.toList(), context(now), HERE, until = if (until) gapEnd else null),
            legs,
        )
    }

    @Test
    fun `drop-in needs a published end of at least four hours`() {
        assertTrue(event("a", start = at(Nov28, 10).toString(), end = at(Nov28, 14).toString()).isDropIn())
        assertFalse(event("b", start = at(Nov28, 10).toString(), end = at(Nov28, 13, 59).toString()).isDropIn())
        assertFalse(event("c", start = at(Nov28, 10).toString(), end = null, type = "exhibition").isDropIn())
        assertFalse(event("d", start = at(Nov28, 10).toString(), end = at(Nov28, 9).toString()).isDropIn())
    }

    @Test
    fun `a run of a day or more is a recurring series, not a drop-in`() {
        assertTrue(event("a", start = at(Nov28, 10).toString(), end = at(Nov28.plusDays(1), 9, 59).toString()).isDropIn())
        assertFalse(event("b", start = at(Nov28, 10).toString(), end = at(Nov28.plusDays(1), 10).toString()).isDropIn())
        assertFalse(event("c", start = at(Nov28, 10).toString(), end = at(Nov28.plusDays(21), 17).toString()).isDropIn())
    }

    @Test
    fun `a drop-in lasts the typical stay, capped by its end, the next Meal and the window`() {
        val exhibition = { end: Int -> event("x", start = at(Nov28, 10).toString(), end = at(Nov28, end).toString(), type = "exhibition") }
        fun leaves(itinerary: Itinerary, e: com.example.hackapp.data.Event) =
            found(itinerary, false, e).single().suggestedDeparture.plannedDeparture.toInstant()
        assertEquals(instant(Nov28, 19), leaves(night(), exhibition(23)))
        val theatre = event("t", start = at(Nov28, 10).toString(), end = at(Nov28, 19, 30).toString(), type = "theatre")
        assertEquals(instant(Nov28, 19, 30), leaves(night(), theatre))
        val concert = event("m", start = at(Nov28, 10).toString(), end = at(Nov28, 23).toString(), type = "music")
        assertEquals(instant(Nov28, 19, 30), leaves(night(blocks = listOf(MealBlock("d", MealKind.Dinner, LocalTime.of(19, 30)))), concert))
        val shortNight = night(end = LocalTime.of(19, 15))
        assertEquals(shortNight.windowEnd(Toronto).toInstant(), leaves(shortNight, concert))
    }

    @Test
    fun `every drop-in needs a 60 minute stay, even one starting inside the gap`() {
        val itinerary = night(blocks = listOf(MealBlock("d", MealKind.Dinner, LocalTime.of(19, 0))))
        val tooShort = event("short", start = at(Nov28, 18, 30).toString(), end = at(Nov28, 23).toString())
        assertTrue(found(itinerary, true, tooShort).isEmpty())
        val enough = event("ok", start = at(Nov28, 18).toString(), end = at(Nov28, 23).toString())
        assertEquals(listOf("ok"), found(itinerary, true, enough).map { it.event.id })
    }

    @Test
    fun `a drop-in leaves in time for the next Stop`() {
        val next = stop("n", at(Nov28, 19, 40), at(Nov28, 20, 30))
        val concert = event("m", start = at(Nov28, 10).toString(), end = at(Nov28, 23).toString())
        val itinerary = night(stops = listOf(next))
        val result = reachableShowtimes(EventSearch(itinerary, listOf(concert), context(now), HERE, gap = TimeGap(null, next)), legs).single()
        assertEquals(instant(Nov28, 19, 20), result.suggestedDeparture.plannedDeparture.toInstant())
    }

    @Test
    fun `a drop-in Event crossing a block stays and leaves when the block starts`() {
        val itinerary = night(blocks = listOf(dinner))
        val longRun = event("long", start = at(Nov28, 18, 30).toString(), end = at(Nov28, 23).toString())
        val fixed = event("fixed", start = at(Nov28, 18, 30).toString(), end = at(Nov28, 21).toString())
        val result = found(itinerary, true, longRun, fixed)
        assertEquals(listOf("long"), result.map { it.event.id })
        assertEquals(at(Nov28, 20).toInstant(), result.single().suggestedDeparture.plannedDeparture.toInstant())
        assertTrue(rankedEventCards(EventSearch(itinerary, listOf(longRun), context(now), HERE), legs).single().isDropIn)
    }

    @Test
    fun `a drop-in Event is not hidden once its block is skipped, and keeps its typical stay`() {
        val skipped = night(blocks = listOf(dinner.copy(skipped = true)))
        val longRun = event("long", start = at(Nov28, 18, 30).toString(), end = at(Nov28, 23).toString())
        val result = found(skipped, true, longRun)
        assertEquals(at(Nov28, 20, 30).toInstant(), result.single().suggestedDeparture.plannedDeparture.toInstant())
    }

    @Test
    fun `showtimes at or after the next block start wait until the Meal is answered`() {
        val itinerary = night(blocks = listOf(dinner))
        val before = event("before", start = at(Nov28, 18, 30).toString(), end = at(Nov28, 19, 30).toString())
        val after = event("after", start = at(Nov28, 21, 30).toString())
        assertEquals(listOf("before"), found(itinerary, true, before, after).map { it.event.id })
        assertEquals(listOf("before", "after"), found(itinerary, false, before, after).map { it.event.id })
        val skipped = itinerary.withMealSkipped("d")
        assertEquals(listOf("before", "after"), found(skipped, true, before, after).map { it.event.id })
    }

    @Test
    fun `the next unresolved block ignores skipped, answered and past blocks`() {
        val lunch = MealBlock("l", MealKind.Lunch, LocalTime.of(18, 0).plusHours(0))
        val itinerary = night(blocks = listOf(lunch, dinner))
        assertEquals("l", itinerary.pendingMealBlock(now, Toronto)?.id)
        assertEquals("d", itinerary.withMealSkipped("l").pendingMealBlock(now, Toronto)?.id)
        val meal = stop("m", at(Nov28, 18), at(Nov28, 19, 30), kind = StopKind.Meal, mealBlockId = "l")
        assertEquals("d", itinerary.copy(stops = listOf(meal)).pendingMealBlock(now, Toronto)?.id)
        val late = itinerary.copy(stops = listOf(stop("e", at(Nov28, 21, 40), at(Nov28, 22))))
        assertNull(late.copy(mealBlocks = listOf(dinner)).pendingMealBlock(now, Toronto))
    }

    @Test
    fun `the gap starts at the last Stop or at the later of window start and now`() {
        assertEquals(instant(Nov28, 18), night().gapStart(now, Toronto))
        assertEquals(instant(Nov28, 19), night().gapStart(instant(Nov28, 19), Toronto))
        val withStop = night(stops = listOf(stop("e", at(Nov28, 18), at(Nov28, 19, 15))))
        assertEquals(instant(Nov28, 19, 15), withStop.gapStart(now, Toronto))
    }

    @Test
    fun `the District follows the saved one, else the last Stop, else the first`() {
        assertEquals(HERE, night().selectionDistrict())
        assertEquals(NEXT, night().copy(planningDistrictId = NEXT).selectionDistrict())
        assertEquals(HERE, night().copy(planningDistrictId = "gone").selectionDistrict())
        val inNext = night(stops = listOf(stop("e", at(Nov28, 18), at(Nov28, 19), district = NEXT)))
        assertEquals(NEXT, inNext.selectionDistrict())
    }

    @Test
    fun `leaving a District drops it only when it has no Stop`() {
        val empty = night()
        val moved = empty.movedToDistrict(HERE, NEXT)
        assertEquals(listOf(NEXT), moved.districtIds)
        assertEquals(NEXT, moved.planningDistrictId)
        val kept = night(stops = listOf(stop("e", at(Nov28, 18), at(Nov28, 19)))).movedToDistrict(HERE, NEXT)
        assertEquals(listOf(HERE, NEXT), kept.districtIds)
        assertEquals(NEXT, night().districtAfter(HERE))
        assertNull(night().districtAfter(NEXT))
        assertNull(night().districtBefore(HERE))
        assertEquals(HERE, night().districtBefore(NEXT))
    }

    @Test
    fun `finishing needs an Event, prunes empty Districts and moves to Review`() {
        assertNull(night().finishedSelection())
        val mealOnly = night(stops = listOf(stop("m", at(Nov28, 20), at(Nov28, 21), kind = StopKind.Meal, mealBlockId = "d")))
        assertNull(mealOnly.finishedSelection())
        val done = night(stops = listOf(stop("e", at(Nov28, 18), at(Nov28, 19)))).finishedSelection()!!
        assertEquals(listOf(HERE), done.districtIds)
        assertEquals(PlanningStep.Review, done.step)
    }

    private val allDay = event("all-day", start = at(Nov28, 10).toString(), end = at(Nov28, 21).toString(), type = "exhibition")

    @Test
    fun `a drop-in already running is offered from the earliest arrival`() {
        val result = found(night(), false, allDay).single()
        assertEquals(at(Nov28, 10).toInstant(), result.start.toInstant())
        assertEquals(at(Nov28, 18).toInstant(), result.arrivalAt.toInstant())
        assertEquals(at(Nov28, 19).toInstant(), result.suggestedDeparture.plannedDeparture.toInstant())
        val after = night(stops = listOf(stop("e", at(Nov28, 18), at(Nov28, 19))))
        val later = reachableShowtimes(EventSearch(after, listOf(allDay), context(now), HERE), legs).single()
        assertEquals(at(Nov28, 19, 20).toInstant(), later.arrivalAt.toInstant())
        val card = rankedEventCards(EventSearch(night(), listOf(allDay), context(now), HERE), legs).single()
        assertEquals(at(Nov28, 18).toInstant(), card.firstStart.toInstant())
    }

    @Test
    fun `the Stop starts at the arrival while the Event keeps its published times`() {
        val showtime = found(night(), false, allDay).single()
        val stop = eventStop(showtime.event, showtime.suggestedDeparture.plannedDeparture, arrival = showtime.arrivalAt)!!
        assertEquals(at(Nov28, 18).toInstant(), stop.start.toInstant())
        assertEquals(at(Nov28, 10).toInstant(), showtime.event.startTime()!!.toInstant())
        assertEquals(at(Nov28, 21).toInstant(), showtime.event.publishedEnd()!!.toInstant())
        assertEquals(showtime.event.showtimeKey(), allDay.showtimeKey())
        assertTrue(found(night(stops = listOf(stop)), false, allDay).isEmpty())
    }

    @Test
    fun `a drop-in ending too soon after arrival is hidden`() {
        val ending = event("soon", start = at(Nov28, 10).toString(), end = at(Nov28, 18, 59).toString())
        assertTrue(found(night(), false, ending).isEmpty())
        val enough = event("ok", start = at(Nov28, 10).toString(), end = at(Nov28, 19).toString())
        assertEquals(listOf("ok"), found(night(), false, enough).map { it.event.id })
    }

    @Test
    fun `a running drop-in crossing a Meal Block leaves when the block starts`() {
        val itinerary = night(blocks = listOf(dinner))
        val concert = allDay.copy(type = "theatre")
        val result = found(itinerary, true, concert).single()
        assertEquals(at(Nov28, 18).toInstant(), result.arrivalAt.toInstant())
        assertEquals(at(Nov28, 20).toInstant(), result.suggestedDeparture.plannedDeparture.toInstant())
        val early = night(blocks = listOf(MealBlock("e", MealKind.Dinner, LocalTime.of(18, 30))))
        assertTrue(found(early, true, allDay).isEmpty())
    }

    @Test
    fun `District counts include a running drop-in with time left`() {
        val window = night().planningWindow(Toronto)
        assertEquals(mapOf(HERE to 1), countEligibleByDistrict(listOf(allDay), window, context(now)))
    }

    @Test
    fun `the budget lists each Meal Stop with its own amount and limit`() {
        val lunch = MealBlock("l", MealKind.Lunch, LocalTime.of(18, 0), com.example.hackapp.profile.BudgetBracket.Under20)
        val late = MealBlock("s", MealKind.LateBite, LocalTime.of(22, 0), null)
        val stops = listOf(
            stop("m1", at(Nov28, 18), at(Nov28, 19, 30), kind = StopKind.Meal, sourceId = "7", mealBlockId = "l"),
            stop("m2", at(Nov28, 22), at(Nov28, 22, 45), kind = StopKind.Meal, sourceId = "8", mealBlockId = "s"),
            stop("m3", at(Nov28, 23), at(Nov28, 23, 30), kind = StopKind.Meal, sourceId = "9"),
        )
        val prices = BudgetPrices(mealPrices = mapOf(7L to com.example.hackapp.data.model.PriceRange(10, 30, false), 8L to null, 9L to com.example.hackapp.data.model.PriceRange(10, 15, false)))
        val summary = budgetSummary(night(blocks = listOf(lunch, late), stops = stops), context(now), prices)
        assertEquals(
            listOf(
                MealAmount("m1", "l", MealKind.Lunch, 30.0, 20),
                MealAmount("m2", "s", MealKind.LateBite, null, null),
                MealAmount("m3", null, null, 15.0, null),
            ),
            summary.meals,
        )
        // A Meal with no budget still adds its price but not a limit.
        assertEquals(45.0, summary.mealsCad, 0.0)
        assertEquals(20, summary.mealBudgetCad)
        assertEquals(1, summary.unknownPriceCount)
        assertTrue(budgetSummary(night(), context(now), BudgetPrices()).meals.isEmpty())
    }
}
