package com.example.hackapp.itinerary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalTime

class MealWindowChangeTest {
    private val lunch = MealBlock("meal-lunch", MealKind.Lunch, LocalTime.of(12, 0))
    private val dinner = MealBlock("meal-dinner", MealKind.Dinner, LocalTime.of(18, 30))

    private fun draft(end: Int, step: PlanningStep) = Itinerary(
        id = "a", ownerUid = UID, cityId = "mtl", step = step,
        date = Nov28, outingKind = OutingKind.Day,
        startTime = LocalTime.of(10, 0), endTime = LocalTime.of(end, 0),
        mealBlocks = listOf(lunch, dinner), createdAtMillis = 0, updatedAtMillis = 0,
    )

    @Test
    fun blocksOutsideTheNewWindowAreDroppedAndTheStepGoesBackToMeals() {
        val result = draft(end = 16, step = PlanningStep.Review).withFittingMealBlocks(Toronto)
        assertEquals(listOf(lunch), result.mealBlocks)
        assertEquals(PlanningStep.Meals, result.step)
    }

    @Test
    fun anEarlierStepIsKept() {
        assertEquals(PlanningStep.Budget, draft(16, PlanningStep.Budget).withFittingMealBlocks(Toronto).step)
    }

    @Test
    fun nothingChangesWhenEveryBlockStillFits() {
        val same = draft(end = 22, step = PlanningStep.Review)
        assertSame(same, same.withFittingMealBlocks(Toronto))
    }
}
