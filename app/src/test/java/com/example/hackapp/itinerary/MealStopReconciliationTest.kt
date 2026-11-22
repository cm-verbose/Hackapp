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
        assertEquals(listOf("m1", "e"), reconcileMealStops(previous, updated, Toronto).stops.map { it.id })
    }

    @Test
    fun movedBlockRemovesItsStopButKeepsEvents() {
        val updated = previous.copy(mealBlocks = listOf(lunch.copy(start = LocalTime.of(12, 30)), dinner))
        assertEquals(listOf("e", "m2"), reconcileMealStops(previous, updated, Toronto).stops.map { it.id })
    }

    @Test
    fun decliningMealsRemovesEveryMealStop() {
        val updated = previous.copy(planMeals = false, mealBlocks = emptyList())
        assertEquals(listOf("e"), reconcileMealStops(previous, updated, Toronto).stops.map { it.id })
    }

    @Test
    fun budgetChangeOnlyKeepsEverything() {
        val updated = previous.copy(mealBlocks = listOf(lunch.copy(budget = com.example.hackapp.profile.BudgetBracket.Under20), dinner))
        assertSame(updated, reconcileMealStops(previous, updated, Toronto))
    }

    @Test
    fun eventOverlappingAMovedBlockIsRemovedAndTheDraftReturnsToSelection() {
        val atReview = previous.copy(step = PlanningStep.Review)
        val updated = atReview.copy(mealBlocks = listOf(lunch, dinner.copy(start = LocalTime.of(19, 45))))
        val saved = reconcileMealStops(atReview, updated, Toronto)
        assertEquals(listOf("m1"), saved.stops.map { it.id })
        assertEquals(PlanningStep.Stops, saved.step)
    }

    @Test
    fun previewListsRemovedAndKeptStopsWithoutChangingTheDraft() {
        val updated = previous.copy(mealBlocks = listOf(lunch, dinner.copy(start = LocalTime.of(19, 45))))
        val preview = previewMealChange(previous, updated, Toronto)
        assertEquals(true, preview.needsConfirmation)
        assertEquals(listOf("e", "m2"), preview.removed.map { it.stop.id })
        assertEquals(
            listOf(StopRemovalReason.NoLongerReachable, StopRemovalReason.MealBlockRemoved),
            preview.removed.map { it.reason },
        )
        assertEquals(listOf("m1"), preview.kept.map { it.id })
        assertEquals(listOf("m1", "e", "m2"), previous.stops.map { it.id })
    }

    @Test
    fun previewNeedsNoConfirmationWhenEveryStopStillFits() {
        val updated = previous.copy(mealBlocks = listOf(lunch.copy(budget = com.example.hackapp.profile.BudgetBracket.Under20), dinner))
        assertEquals(false, previewMealChange(previous, updated, Toronto).needsConfirmation)
    }

    @Test
    fun skippedBlockDoesNotRemoveAnEvent() {
        val updated = previous.copy(mealBlocks = listOf(lunch, dinner.copy(start = LocalTime.of(19, 45), skipped = true)))
        assertEquals(listOf("m1", "e"), reconcileMealStops(previous, updated, Toronto).stops.map { it.id })
    }

    @Test
    fun aBlockLeftBeforeTheLastStopStillGetsThePrompt() {
        val updated = previous.copy(mealBlocks = listOf(lunch, dinner.copy(start = LocalTime.of(18, 0))))
        val saved = reconcileMealStops(previous, updated, Toronto)
        assertEquals(listOf("m1", "e"), saved.stops.map { it.id })
        val morning = instant(Nov28, 8)
        assertEquals(null, saved.pendingMealBlock(morning, Toronto))
        assertEquals("meal-dinner", saved.mealBlockToAnswer(morning, Toronto)?.id)
        assertEquals(null, saved.mealBlockToAnswer(instant(Nov28, 20), Toronto))
    }
}
