package com.example.hackapp.itinerary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class SelectionGuidanceTest {
    private val legs = fixedLegs()
    private val now = instant(Nov28, 8)

    @Test
    fun `moving on is suggested after an Event, unless a Meal comes right after it`() {
        val withEvent = day(stops = listOf(stop("e", at(Nov28, 11), at(Nov28, 12))))
        assertTrue(withEvent.suggestsNextDistrict(HERE, mealPromptReached = false))
        assertFalse(withEvent.suggestsNextDistrict(HERE, mealPromptReached = true))

        val withMeal = day(
            stops = listOf(
                stop("e", at(Nov28, 11), at(Nov28, 12)),
                stop("m", at(Nov28, 12), at(Nov28, 13), kind = StopKind.Meal, mealBlockId = "l"),
            ),
        )
        assertTrue(withMeal.suggestsNextDistrict(HERE, mealPromptReached = true))
    }

    @Test
    fun `moving on is never suggested without an Event here or on the last District`() {
        val mealOnly = day(stops = listOf(stop("m", at(Nov28, 12), at(Nov28, 13), kind = StopKind.Meal)))
        assertFalse(mealOnly.suggestsNextDistrict(HERE, mealPromptReached = false))
        val lastDistrict = day(stops = listOf(stop("e", at(Nov28, 11), at(Nov28, 12), district = NEXT)))
        assertFalse(lastDistrict.suggestsNextDistrict(NEXT, mealPromptReached = false))
    }

    @Test
    fun `a late first District offers the order of the day`() {
        val itinerary = day()
        val events = listOf(
            event("late", start = at(Nov28, 16).toString()),
            event("early", start = at(Nov28, 11).toString(), district = NEXT),
        )
        val starts = earliestEventStarts(itinerary, events, context(now), legs)
        assertEquals(instant(Nov28, 16), starts[HERE])
        assertEquals(listOf(NEXT, HERE), itinerary.chronologicalDistrictOrder(starts, instant(Nov28, 10)))
    }

    @Test
    fun `no reorder when the first District starts soon, the gain is small, or Stops exist`() {
        val itinerary = day()
        val soon = mapOf(HERE to instant(Nov28, 11), NEXT to instant(Nov28, 10, 30))
        assertNull(itinerary.chronologicalDistrictOrder(soon, instant(Nov28, 10)))
        val smallGain = mapOf(HERE to instant(Nov28, 13), NEXT to instant(Nov28, 12, 30))
        assertNull(itinerary.chronologicalDistrictOrder(smallGain, instant(Nov28, 10)))
        val planned = day(stops = listOf(stop("e", at(Nov28, 16), at(Nov28, 17))))
        assertNull(planned.chronologicalDistrictOrder(mapOf(HERE to instant(Nov28, 16), NEXT to instant(Nov28, 11)), instant(Nov28, 10)))
    }

    @Test
    fun `an empty first District moves behind one with Events`() {
        val starts = mapOf(HERE to null, NEXT to instant(Nov28, 15))
        assertEquals(listOf(NEXT, HERE), day().chronologicalDistrictOrder(starts, instant(Nov28, 10)))
    }

    @Test
    fun `a Meal that hides every Event is a conflict, and moving it brings them back`() {
        val lunch = MealBlock("l", MealKind.Lunch, LocalTime.of(12, 0))
        val itinerary = day(blocks = listOf(lunch))
        val events = listOf(
            event("a", start = at(Nov28, 12).toString()),
            event("b", start = at(Nov28, 12, 30).toString()),
        )
        val impact = mealImpact(itinerary, "l", events, context(now), listOf(HERE), legs)
        assertEquals(0, impact.withMeal)
        assertEquals(2, impact.withoutMeal)
        assertTrue(impact.isConflict)

        val moved = itinerary.withMealStart("l", LocalTime.of(16, 0), Toronto)
        assertNotNull(moved)
        assertEquals(2, remainingEventCount(moved!!, events, context(now), listOf(HERE), legs))
    }

    @Test
    fun `losing one Event out of many is not a conflict`() {
        val impact = MealImpact("l", withMeal = 9, withoutMeal = 10)
        assertFalse(impact.isConflict)
        assertTrue(MealImpact("l", withMeal = 3, withoutMeal = 6).isConflict)
    }

    @Test
    fun `a Meal cannot move outside the window, onto another Meal or onto a Stop`() {
        val lunch = MealBlock("l", MealKind.Lunch, LocalTime.of(12, 0))
        val dinner = MealBlock("d", MealKind.Dinner, LocalTime.of(15, 0))
        val itinerary = day(blocks = listOf(lunch, dinner), stops = listOf(stop("e", at(Nov28, 10), at(Nov28, 11))))
        assertNull(itinerary.withMealStart("l", LocalTime.of(17, 0), Toronto))
        assertNull(itinerary.withMealStart("l", LocalTime.of(14, 0), Toronto))
        assertNull(itinerary.withMealStart("l", LocalTime.of(10, 30), Toronto))
        assertEquals(LocalTime.of(12, 30), itinerary.withMealStart("l", LocalTime.of(12, 30), Toronto)?.mealBlocks?.first()?.start)
    }
}
