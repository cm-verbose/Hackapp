package com.example.hackapp.scaffold.screens.planning

import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.MealBlock
import com.example.hackapp.itinerary.MealBudgets
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.itinerary.OutingKind
import com.example.hackapp.profile.BudgetBracket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PlanningBudgetTest {
    private val draft = Itinerary(
        id = "a",
        ownerUid = "u",
        cityId = "mtl",
        date = LocalDate.of(2026, 11, 28),
        outingKind = OutingKind.Night,
        startTime = LocalTime.of(18, 0),
        endTime = LocalTime.of(2, 0),
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )

    @Test
    fun editsUpdateTheItineraryAndItsMealBlocks() {
        val blocks = listOf(
            MealBlock("d", MealKind.Dinner, LocalTime.of(18, 30), BudgetBracket.Under20),
            MealBlock("l", MealKind.LateBite, LocalTime.of(22, 0), BudgetBracket.Under20),
        )
        val updated = draft.copy(mealBlocks = blocks).withBudgets(
            BudgetSelection(
                lunch = BudgetBracket.Under20,
                dinner = BudgetBracket.Under100,
                events = BudgetBracket.Under50,
                lateNightEnabled = false,
                lateNight = BudgetBracket.Under20,
            ),
        )
        assertEquals(BudgetBracket.Under50, updated.eventBudget)
        assertEquals(BudgetBracket.Under100, updated.mealBlocks[0].budget)
        assertNull(updated.mealBlocks[1].budget)
        assertNull(updated.mealBudgets?.lateNight)
    }

    @Test
    fun turnedOffLateNightStaysOffWhenReopened() {
        val saved = draft.copy(mealBudgets = MealBudgets(BudgetBracket.Under20, BudgetBracket.Under50, null))
        val selection = budgetSelection(saved, profile = null)
        assertFalse(selection.lateNightEnabled)
        assertEquals(BudgetBracket.Under50, selection.dinner)
    }

    @Test
    fun enabledLateNightNeedsABracket() {
        val base = BudgetSelection(BudgetBracket.Under20, BudgetBracket.Under20, BudgetBracket.Under20)
        assertTrue(base.isComplete)
        assertFalse(base.copy(lateNightEnabled = true).isComplete)
        assertTrue(base.copy(lateNightEnabled = true, lateNight = BudgetBracket.Under20).isComplete)
    }
}
