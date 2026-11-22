package com.example.hackapp.domain

import com.example.hackapp.data.model.MealSlot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T13a: rules of StopSelection (`ItineraryPlanner.selection`).
 * Spec: prep/design/screen-list.md, Planning 4. Tests marked EXPECTED TO FAIL reproduce a confirmed
 * defect and must pass after the fix task named in the comment.
 */
class PlannerSelectionTest {
    private fun ids(view: SelectionView) = view.blocks.flatMap { b -> b.groups.flatMap { it.second } }.map { it.id }.toSet()

    @Test fun firstStopOffersEventsFromTheWindowStart() = runBlocking {
        val p = planner(listOf(ev("early", "09:00"), ev("ok", "10:00"), ev("late", "23:30")))
        val view = p.selection(input(), DISTRICT, previous = null, meal = null, mealChosen = false, chosenIds = emptySet())
        assertEquals(setOf("ok"), ids(view))
    }

    @Test fun reachableMeansPreviousDeparturePlusLegAndBuffer() = runBlocking {
        // Previous Stop leaves at 14:00; every Leg is 10 min travel + 5 min buffer.
        val p = planner(listOf(ev("tooSoon", "14:10"), ev("exact", "14:15"), ev("later", "15:00")))
        val prev = stop("prev", "13:00", "14:00")
        val view = p.selection(input(), DISTRICT, prev, meal = null, mealChosen = false, chosenIds = setOf("prev"))
        assertEquals(setOf("exact", "later"), ids(view))
    }

    @Test fun alreadyChosenEventsAreNotOfferedAgain() = runBlocking {
        val p = planner(listOf(ev("a", "11:00"), ev("b", "12:00")))
        val view = p.selection(input(), DISTRICT, null, null, false, chosenIds = setOf("a"))
        assertEquals(setOf("b"), ids(view))
    }

    @Test fun eventsAreGroupedInOneHourBlocksInTimeOrder() = runBlocking {
        val p = planner(listOf(ev("b", "11:40"), ev("a", "11:05"), ev("c", "13:00", type = "comedy")))
        val view = p.selection(input(), DISTRICT, null, null, false, emptySet())
        assertEquals(listOf(at("11:00"), at("13:00")), view.blocks.map { it.start })
        assertEquals(listOf("a", "b"), view.blocks[0].groups.single().second.map { it.id })
    }

    @Test fun eventsStartingInsideAnUnchosenMealBlockAreHeldBack() = runBlocking {
        val meal = MealBlock(MealSlot.LUNCH, at("12:00")) // block 11:45-13:15
        val p = planner(listOf(ev("inBlock", "12:30"), ev("after", "13:15")))
        val view = p.selection(input(), DISTRICT, null, meal, mealChosen = false, chosenIds = emptySet())
        assertEquals(setOf("after"), ids(view))
        assertNotNull("the meal prompt stays until a Meal is chosen", view.mealPrompt)
    }

    @Test fun mealBlockNoLongerHoldsEventsBackOnceTheMealIsChosen() = runBlocking {
        val meal = MealBlock(MealSlot.LUNCH, at("12:00"))
        val p = planner(listOf(ev("inBlock", "12:30")))
        val view = p.selection(input(), DISTRICT, null, meal, mealChosen = true, chosenIds = emptySet())
        assertTrue("inBlock" in ids(view))
        assertEquals(null, view.mealPrompt)
    }

    /**
     * EXPECTED TO FAIL until T7 (F2): an Event that starts before the Meal Block but runs into it
     * overlaps the meal and must be held back too. Today only the start time is checked.
     */
    @Test fun eventsOverlappingAnUnchosenMealBlockAreHeldBack() = runBlocking {
        val meal = MealBlock(MealSlot.LUNCH, at("12:00")) // block 11:45-13:15
        val p = planner(listOf(ev("overlaps", "11:00", end = "12:30")))
        val view = p.selection(input(), DISTRICT, null, meal, mealChosen = false, chosenIds = emptySet())
        assertEquals(emptySet<String>(), ids(view))
    }
}
