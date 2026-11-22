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
    fun `a drop-in Event is not hidden once its block is skipped, and keeps its own end`() {
        val skipped = night(blocks = listOf(dinner.copy(skipped = true)))
        val longRun = event("long", start = at(Nov28, 18, 30).toString(), end = at(Nov28, 23).toString())
        val result = found(skipped, true, longRun)
        assertEquals(at(Nov28, 23).toInstant(), result.single().suggestedDeparture.plannedDeparture.toInstant())
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
}
