package com.example.hackapp.itinerary

import com.example.hackapp.profile.BudgetBracket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PlanningChangesTest {
    private val legs = fixedLegs(20)
    private val dec5 = LocalDate.of(2026, 12, 5)

    private val event1 = stop("e1", at(Nov28, 19), at(Nov28, 20))
    private val event2 = stop("e2", at(Nov28, 21), at(Nov28, 22), district = NEXT)
    private val dinnerBlock = MealBlock("meal-dinner", MealKind.Dinner, LocalTime.of(20, 30), budget = BudgetBracket.Under50)
    private val meal = stop(
        "m", at(Nov28, 20, 30), at(Nov28, 22), kind = StopKind.Meal, sourceId = "7", mealBlockId = "meal-dinner",
    )

    private fun preview(
        previous: Itinerary,
        updated: Itinerary,
        places: Map<Long, com.example.hackapp.data.model.Place> = emptyMap(),
        isOpenAt: (com.example.hackapp.data.model.Place, java.time.ZonedDateTime) -> Boolean = { _, _ -> true },
    ) = previewPlanningChange(previous, updated.withFittingMealBlocks(Toronto), legs, Toronto, places, isOpenAt)

    private fun reasons(p: PlanningChangePreview) = p.removed.associate { it.stop.id to it.reason }

    @Test
    fun `an Itinerary with no Stops is never warned`() {
        val previous = night()
        val p = preview(previous, previous.copy(date = dec5))
        assertFalse(p.needsConfirmation)
        assertEquals(dec5, p.proposed.date)
    }

    @Test
    fun `a new date removes Events and moves Meals at the same clock time`() {
        val previous = night(stops = listOf(event1, meal), blocks = listOf(dinnerBlock))
        val p = preview(previous, previous.copy(date = dec5))
        assertEquals(mapOf("e1" to StopRemovalReason.DateChanged), reasons(p))
        assertTrue(p.needsConfirmation)
        assertEquals(listOf(meal.copy(start = at(dec5, 20, 30), plannedDeparture = at(dec5, 22))), p.kept)
        assertEquals(p.kept, p.proposed.stops)
        // Nothing was saved: the previous Itinerary is untouched.
        assertEquals(listOf(event1, meal), previous.stops)
    }

    @Test
    fun `a Meal that is closed on the new date is removed`() {
        val previous = night(stops = listOf(meal), blocks = listOf(dinnerBlock))
        val place = place(7)
        val p = preview(previous, previous.copy(date = dec5), mapOf(7L to place)) { _, _ -> false }
        assertEquals(mapOf("m" to StopRemovalReason.MealClosed), reasons(p))
    }

    @Test
    fun `a shorter window removes only the Stops outside it`() {
        val previous = night(stops = listOf(event1, event2))
        val p = preview(previous, previous.copy(endTime = LocalTime.of(20, 30)))
        assertEquals(mapOf("e2" to StopRemovalReason.OutsideWindow), reasons(p))
        assertEquals(listOf("e1"), p.kept.map { it.id })
    }

    @Test
    fun `an Event that now ends after the window is trimmed, not removed`() {
        val previous = night(stops = listOf(event1))
        val p = preview(previous, previous.copy(endTime = LocalTime.of(19, 30)).copy(startTime = LocalTime.of(17, 30)))
        // 17:30-19:30 is a valid window of 2 hours; the Event starts at 19:00 and leaves at the window end.
        assertFalse(p.needsConfirmation)
        assertEquals(at(Nov28, 19, 30), p.kept.single().plannedDeparture)
    }

    @Test
    fun `a Meal Block that no longer fits removes its Stop and goes back to the Meal step`() {
        val previous = night(stops = listOf(event1, meal), blocks = listOf(dinnerBlock)).copy(step = PlanningStep.Review)
        val p = preview(previous, previous.copy(endTime = LocalTime.of(21, 0)))
        assertEquals(mapOf("m" to StopRemovalReason.MealBlockRemoved), reasons(p))
        assertEquals(emptyList<MealBlock>(), p.proposed.mealBlocks)
        assertEquals(PlanningStep.Meals, p.proposed.step)
    }

    @Test
    fun `a removed District removes its Stops and keeps the others`() {
        val previous = night(stops = listOf(event1, event2))
        val p = preview(previous, previous.copy(districtIds = listOf(HERE)))
        assertEquals(mapOf("e2" to StopRemovalReason.DistrictRemoved), reasons(p))
        assertEquals(listOf("e1"), p.proposed.stops.map { it.id })
    }

    @Test
    fun `an empty District list does not remove anything`() {
        val previous = night(stops = listOf(event1))
        assertFalse(preview(previous, previous.copy(districtIds = emptyList())).needsConfirmation)
    }

    @Test
    fun `a Stop that transit cannot reach in time is removed and later ones are checked against the last kept`() {
        val tight = stop("t", at(Nov28, 20, 10), at(Nov28, 21))
        val later = stop("l", at(Nov28, 20, 40), at(Nov28, 21, 30))
        val previous = night(stops = listOf(event1, tight, later))
        val p = preview(previous, previous)
        // event1 leaves 20:00, the Leg takes 20 minutes: 20:20 is after 20:10, so t is lost; l (20:40) is reachable.
        assertEquals(mapOf("t" to StopRemovalReason.NoLongerReachable), reasons(p))
        assertEquals(listOf("e1", "l"), p.kept.map { it.id })
    }

    @Test
    fun `a Meal over the new Meal budget is removed, Events never are`() {
        val previous = night(stops = listOf(event1, meal), blocks = listOf(dinnerBlock))
        val cheaper = BudgetSummaryFree.update(previous, BudgetBracket.Under20)
        val pricey = place(7, price = com.example.hackapp.data.model.PriceRange(30, 50, estimated = false))
        val p = preview(previous, cheaper, mapOf(7L to pricey))
        assertEquals(mapOf("m" to StopRemovalReason.MealOverBudget), reasons(p))
        assertEquals(listOf("e1"), p.kept.map { it.id })
    }

    @Test
    fun `a Meal is kept when its bracket did not change or its price is unknown`() {
        val previous = night(stops = listOf(meal), blocks = listOf(dinnerBlock))
        val pricey = place(7, price = com.example.hackapp.data.model.PriceRange(30, 50, estimated = false))
        assertFalse(preview(previous, previous, mapOf(7L to pricey)).needsConfirmation)
        val cheaper = BudgetSummaryFree.update(previous, BudgetBracket.Under20)
        assertFalse(preview(previous, cheaper, mapOf(7L to place(7, price = null))).needsConfirmation)
        assertFalse(preview(previous, cheaper, emptyMap()).needsConfirmation)
        // A bracket raised above the price keeps the Meal.
        val richer = BudgetSummaryFree.update(previous, BudgetBracket.Under100)
        assertFalse(preview(previous, richer, mapOf(7L to pricey)).needsConfirmation)
    }

    @Test
    fun `reconciling its own result changes nothing`() {
        val previous = night(stops = listOf(event1, event2, meal), blocks = listOf(dinnerBlock))
        val first = preview(previous, previous.copy(date = dec5, districtIds = listOf(NEXT)))
        assertTrue(first.needsConfirmation)
        val second = preview(first.proposed, first.proposed)
        assertFalse(second.needsConfirmation)
        assertEquals(first.proposed, second.proposed)
    }

    @Test
    fun `the preview lists every removed and kept Stop`() {
        val previous = night(stops = listOf(event1, event2, meal), blocks = listOf(dinnerBlock))
        val p = preview(previous, previous.copy(date = dec5))
        assertEquals(setOf("e1", "e2"), p.removed.map { it.stop.id }.toSet())
        assertEquals(listOf("m"), p.kept.map { it.id })
    }
}

/** Test helper: sets one bracket on the Meal Block and the Itinerary's Meal budgets. */
private object BudgetSummaryFree {
    fun update(itinerary: Itinerary, bracket: BudgetBracket): Itinerary = itinerary.copy(
        mealBudgets = MealBudgets(dinner = bracket),
        mealBlocks = itinerary.mealBlocks.map { it.copy(budget = bracket) },
    )
}

class MealTravelTest {
    private val legs = fixedLegs(20)
    private val block = MealBlock("meal-dinner", MealKind.Dinner, LocalTime.of(19, 0))
    private val e1 = stop("e1", at(Nov28, 18), at(Nov28, 19))
    private val meal = stop(
        "m", at(Nov28, 19), at(Nov28, 20, 30), kind = StopKind.Meal, sourceId = "7", mealBlockId = "meal-dinner",
    )
    private val e2 = stop("e2", at(Nov28, 20, 30), at(Nov28, 21, 30))

    @Test
    fun `Event, Meal and Event back to back are not flagged for transit`() {
        val itinerary = night(stops = listOf(e1, meal, e2), blocks = listOf(block))
        val p = previewDraftEdit(itinerary, DraftEdit.ChangeWindow(itinerary.startTime, itinerary.endTime), legs, Toronto)
        assertTrue(p.removed.isEmpty())
    }

    @Test
    fun `an edit that changes nothing gives no warning`() {
        val itinerary = night(stops = listOf(e1, meal, e2), blocks = listOf(block))
        val p = previewPlanningChange(itinerary, itinerary, legs, Toronto)
        assertFalse(p.needsConfirmation)
        assertEquals(itinerary.stops, p.proposed.stops)
    }

    @Test
    fun `a moved Meal must be open for its whole block, not only its start`() {
        val itinerary = night(stops = listOf(meal), blocks = listOf(block))
        val moved = itinerary.copy(date = LocalDate.of(2026, 12, 5))
        val closesAt20 = { _: com.example.hackapp.data.model.Place, time: java.time.ZonedDateTime ->
            time.toLocalTime() < LocalTime.of(20, 0)
        }
        val p = previewPlanningChange(itinerary, moved, legs, Toronto, mapOf(7L to place(7)), closesAt20)
        assertEquals(StopRemovalReason.MealClosed, p.removed.single().reason)
        val open = previewPlanningChange(itinerary, moved, legs, Toronto, mapOf(7L to place(7))) { _, _ -> true }
        assertFalse(open.needsConfirmation)
    }

    @Test
    fun `a date and window edit together resolves a moved Meal against the new start`() {
        val late = MealBlock("meal-late", MealKind.LateBite, LocalTime.of(1, 0))
        val lateMeal = stop(
            "l", at(Nov28.plusDays(1), 1), at(Nov28.plusDays(1), 1, 45),
            kind = StopKind.Meal, sourceId = "7", mealBlockId = "meal-late",
        )
        val previous = night(stops = listOf(lateMeal), blocks = listOf(late))
        val dec5 = LocalDate.of(2026, 12, 5)
        val updated = previous.copy(date = dec5, startTime = LocalTime.MIDNIGHT, endTime = LocalTime.of(3, 0))
            .withFittingMealBlocks(Toronto)
        val p = previewPlanningChange(previous, updated, legs, Toronto)
        assertTrue(p.removed.isEmpty())
        assertEquals(at(dec5, 1), p.kept.single().start)
    }
}
