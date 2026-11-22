package com.example.hackapp.itinerary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalTime

class MealStopReconciliationTest {
    private val lunch = MealBlock("meal-lunch", MealKind.Lunch, LocalTime.of(12, 0))
    private val dinner = MealBlock("meal-dinner", MealKind.Dinner, LocalTime.of(18, 30))
    private val event = stop("e", at(Nov28, 20), at(Nov28, 21))
    private val lunchStop = stop("m1", at(Nov28, 12), at(Nov28, 13), kind = StopKind.Meal, mealBlockId = "meal-lunch")
    private val dinnerStop = stop("m2", at(Nov28, 18, 30), at(Nov28, 19, 30), kind = StopKind.Meal, mealBlockId = "meal-dinner")

    private val previous = night(start = LocalTime.of(10, 0), end = LocalTime.of(23, 0), blocks = listOf(lunch, dinner))
        .copy(stops = listOf(lunchStop, event, dinnerStop))

    @Test
    fun removedBlockRemovesItsStop() {
        val updated = previous.copy(mealBlocks = listOf(lunch))
        assertEquals(listOf("m1", "e"), reconcileMealStops(previous, updated).stops.map { it.id })
    }

    @Test
    fun movedBlockRemovesItsStopButKeepsEvents() {
        val updated = previous.copy(mealBlocks = listOf(lunch.copy(start = LocalTime.of(12, 30)), dinner))
        assertEquals(listOf("e", "m2"), reconcileMealStops(previous, updated).stops.map { it.id })
    }

    @Test
    fun decliningMealsRemovesEveryMealStop() {
        val updated = previous.copy(planMeals = false, mealBlocks = emptyList())
        assertEquals(listOf("e"), reconcileMealStops(previous, updated).stops.map { it.id })
    }

    @Test
    fun budgetChangeOnlyKeepsEverything() {
        val updated = previous.copy(mealBlocks = listOf(lunch.copy(budget = com.example.hackapp.profile.BudgetBracket.Under20), dinner))
        assertSame(updated, reconcileMealStops(previous, updated))
    }
}
