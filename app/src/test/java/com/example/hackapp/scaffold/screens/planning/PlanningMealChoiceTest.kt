package com.example.hackapp.scaffold.screens.planning

import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.MealBlock
import com.example.hackapp.itinerary.MealBudgets
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.itinerary.OutingKind
import com.example.hackapp.itinerary.PlanningContext
import com.example.hackapp.itinerary.PlanningStep
import com.example.hackapp.profile.AgeRange
import com.example.hackapp.profile.BudgetBracket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PlanningMealChoiceTest {
    private val zone = ZoneId.of("America/Toronto")

    private fun draft(start: Int, end: Int, planMeals: Boolean? = null, blocks: List<MealBlock> = emptyList()) =
        Itinerary(
            id = "a", ownerUid = "u", cityId = "mtl",
            date = LocalDate.of(2026, 11, 28),
            outingKind = OutingKind.Day,
            startTime = LocalTime.of(start, 0),
            endTime = LocalTime.of(end, 0),
            planMeals = planMeals,
            mealBlocks = blocks,
            createdAtMillis = 0, updatedAtMillis = 0,
        )

    private fun context(lunch: BudgetBracket? = null) = PlanningContext(
        now = Instant.EPOCH, zone = zone, ageRange = AgeRange.From25To34,
        defaultMealBudgets = mapOf(MealKind.Lunch to lunch, MealKind.LateBite to BudgetBracket.Under20),
    )

    private fun options(it: Itinerary, picks: Map<MealKind, MealPick> = initialMealPicks(it, context()), ctx: PlanningContext = context()) =
        mealChoiceOptions(it, picks, ctx)

    @Test
    fun offersOnlyBlocksThatFitTheWindow() {
        // 10:00-18:00: Lunch fits, Dinner (18:30) and Late bite do not.
        assertEquals(listOf(MealKind.Lunch), options(draft(10, 18)).map { it.kind })
        // 18:00-02:00 crosses midnight: Dinner and Late bite (22:00-22:45) fit.
        assertEquals(listOf(MealKind.Dinner, MealKind.LateBite), options(draft(18, 2)).map { it.kind })
    }

    @Test
    fun blockMustFitWholeNotJustStart() {
        // Window ends 13:00: Lunch 12:00-13:30 does not fit.
        assertTrue(options(draft(10, 13)).isEmpty())
        // Ends 13:30: it fits exactly.
        assertEquals(listOf(MealKind.Lunch), options(draft(10, 14).copy(endTime = LocalTime.of(13, 30))).map { it.kind })
    }

    @Test
    fun firstOpenSelectsEveryFittingMealAndRespectsDeclined() {
        assertTrue(options(draft(10, 22)).all { it.selected })
        val declined = draft(10, 22, planMeals = false)
        assertTrue(options(declined).none { it.selected })
    }

    @Test
    fun reopeningRestoresSavedChoices() {
        val saved = draft(
            10, 22, planMeals = true,
            blocks = listOf(MealBlock("meal-dinner", MealKind.Dinner, LocalTime.of(19, 0), BudgetBracket.Under50)),
        )
        val result = options(saved)
        assertEquals(listOf(MealKind.Lunch, MealKind.Dinner), result.map { it.kind })
        assertFalse(result[0].selected)
        assertTrue(result[1].selected)
        assertEquals(LocalTime.of(19, 0), result[1].start)
        assertEquals(LocalTime.of(20, 30), result[1].endTime)
        assertEquals(BudgetBracket.Under50, result[1].budget)
    }

    @Test
    fun togglingOffIsPossibleViaPicks() {
        val it = draft(10, 22)
        val picks = initialMealPicks(it, context())
        val off = picks + (MealKind.Lunch to picks.getValue(MealKind.Lunch).copy(selected = false))
        assertFalse(options(it, off).first { o -> o.kind == MealKind.Lunch }.selected)
    }

    @Test
    fun timeLeavingTheWindowIsInvalid() {
        val it = draft(10, 14)
        val picks = initialMealPicks(it, context())
        fun errorAt(h: Int, m: Int) = options(it, picks + (MealKind.Lunch to picks.getValue(MealKind.Lunch).copy(start = LocalTime.of(h, m))))
            .single().error
        assertNull(errorAt(12, 30))
        assertNull(errorAt(12, 30))
        assertEquals(MealTimeError.OutsideWindow, errorAt(12, 31))
        assertEquals(MealTimeError.OutsideWindow, errorAt(9, 0))
    }

    @Test
    fun pastMidnightTimesBelongToTheNextDay() {
        val it = draft(18, 2)
        val picks = initialMealPicks(it, context())
        fun errorAt(h: Int, m: Int) = options(it, picks + (MealKind.LateBite to picks.getValue(MealKind.LateBite).copy(start = LocalTime.of(h, m))))
            .first { o -> o.kind == MealKind.LateBite }.error
        assertNull(errorAt(0, 30))
        assertNull(errorAt(1, 15))
        assertEquals(MealTimeError.OutsideWindow, errorAt(1, 16))
        assertEquals(MealTimeError.OutsideWindow, errorAt(3, 0))
        assertEquals(MealTimeError.OutsideWindow, errorAt(17, 0))
    }

    @Test
    fun overlappingSelectedBlocksAreInvalidButTouchingOnesAreNot() {
        val it = draft(10, 22)
        val picks = initialMealPicks(it, context())
        fun with(dinner: LocalTime) = options(it, picks + (MealKind.Dinner to picks.getValue(MealKind.Dinner).copy(start = dinner)))
        // Lunch 12:00-13:30; Dinner at 13:30 touches.
        assertTrue(with(LocalTime.of(13, 30)).none { o -> o.error != null })
        val overlapping = with(LocalTime.of(13, 0))
        assertEquals(MealTimeError.Overlaps, overlapping.first { o -> o.kind == MealKind.Lunch }.error)
        assertEquals(MealTimeError.Overlaps, overlapping.first { o -> o.kind == MealKind.Dinner }.error)
        // Deselecting one clears the overlap.
        val off = picks + (MealKind.Lunch to picks.getValue(MealKind.Lunch).copy(selected = false)) +
            (MealKind.Dinner to picks.getValue(MealKind.Dinner).copy(start = LocalTime.of(13, 0)))
        assertTrue(options(it, off).none { o -> o.error != null })
    }

    @Test
    fun deselectedBudgetFallsBackToTheProfile() {
        val it = draft(10, 14)
        val ctx = context(lunch = BudgetBracket.Under20)
        val picks = initialMealPicks(it, context())
        val none = options(it, picks, ctx).single()
        assertNull(none.budget)
        assertEquals(BudgetBracket.Under20, none.effectiveBudget)
        val chosen = options(it, picks + (MealKind.Lunch to picks.getValue(MealKind.Lunch).copy(budget = BudgetBracket.Under100)), ctx).single()
        assertEquals(BudgetBracket.Under100, chosen.effectiveBudget)
        // An Itinerary override wins over the Profile.
        val overridden = it.copy(mealBudgets = MealBudgets(lunch = BudgetBracket.Under50))
        assertEquals(BudgetBracket.Under50, options(overridden, initialMealPicks(overridden, context()), ctx).single().effectiveBudget)
    }

    @Test
    fun savingWritesBlocksInWindowOrderAndKeepsIds() {
        val it = draft(18, 2, planMeals = true, blocks = listOf(MealBlock("custom-id", MealKind.LateBite, LocalTime.of(22, 0))))
        val picks = initialMealPicks(it, context())
        val saved = it.withMealChoice(true, picks, zone)
        assertEquals(true, saved.planMeals)
        assertEquals(listOf(MealKind.LateBite), saved.mealBlocks.map { it.kind })
        assertEquals("custom-id", saved.mealBlocks.single().id)
        val both = it.copy(mealBlocks = emptyList(), planMeals = null).let { d ->
            d.withMealChoice(true, initialMealPicks(d, context()), zone)
        }
        assertEquals(listOf(MealKind.Dinner, MealKind.LateBite), both.mealBlocks.map { it.kind })
        assertEquals("meal-dinner", both.mealBlocks[0].id)
    }

    @Test
    fun decliningReservesNoBlocks() {
        val it = draft(10, 22)
        val saved = it.withMealChoice(false, initialMealPicks(it, context()), zone)
        assertEquals(false, saved.planMeals)
        assertTrue(saved.mealBlocks.isEmpty())
        assertEquals(PlanningStep.DateTime, saved.step)
    }

    @Test
    fun canContinueRules() {
        val ok = MealChoiceOption(MealKind.Lunch, true, LocalTime.NOON, LocalTime.of(13, 30), null, null, null)
        val base = PlanningMealChoiceUiState(loading = false, options = listOf(ok))
        assertTrue(base.canContinue)
        assertFalse(base.copy(options = listOf(ok.copy(error = MealTimeError.Overlaps))).canContinue)
        assertFalse(base.copy(options = listOf(ok.copy(selected = false))).canContinue)
        assertFalse(base.copy(loading = true).canContinue)
        assertFalse(base.copy(saving = true).canContinue)
        val none = PlanningMealChoiceUiState(loading = false, noMealFits = true)
        assertFalse(none.canContinue)
        assertTrue(none.copy(planMeals = false).canContinue)
        assertTrue(base.copy(planMeals = false, options = listOf(ok.copy(error = MealTimeError.Overlaps))).canContinue)
    }

    @Test
    fun windowIsUnknownUntilLoaded() {
        val state = PlanningMealChoiceUiState()
        assertNull(state.windowStart)
        assertNull(state.windowEnd)
    }

    @Test
    fun lateBiteIsNotPreselectedWhenItsAddOnIsOff() {
        val it = draft(18, 2)
        val off = PlanningContext(now = Instant.EPOCH, zone = zone, ageRange = AgeRange.From25To34)
        val picks = initialMealPicks(it, off)
        assertTrue(picks.getValue(MealKind.Dinner).selected)
        assertFalse(picks.getValue(MealKind.LateBite).selected)
        assertTrue(picks.getValue(MealKind.LateBite).offered)
        // Turned on by the Profile budget, it is preselected.
        assertTrue(initialMealPicks(it, context()).getValue(MealKind.LateBite).selected)
    }

    @Test
    fun keptBlockKeepsItsSkippedFlag() {
        val it = draft(18, 2, planMeals = true, blocks = listOf(MealBlock("d", MealKind.Dinner, LocalTime.of(18, 30), skipped = true)))
        val saved = it.withMealChoice(true, initialMealPicks(it, context()), zone)
        assertTrue(saved.mealBlocks.single().skipped)
    }
}
