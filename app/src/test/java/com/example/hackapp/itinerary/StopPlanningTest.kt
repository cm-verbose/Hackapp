package com.example.hackapp.itinerary

import com.example.hackapp.data.Cost
import com.example.hackapp.data.CostStatus
import com.example.hackapp.data.model.PriceRange
import com.example.hackapp.profile.BudgetBracket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class StopPlanningTest {
    private val legs = fixedLegs(20)
    private val nextDay = Nov28.plusDays(1)

    @Test
    fun `typical stay falls back for unknown types`() {
        assertEquals(120, typicalStayMinutes("cinema"))
        assertEquals(150, typicalStayMinutes("THEATRE"))
        assertEquals(90, typicalStayMinutes("mystery"))
    }

    @Test
    fun `missing end prefills the typical stay without inventing an end`() {
        val suggestion = suggestDeparture(event("a", start = at(Nov28, 19).toString(), type = "cinema"), night(), Toronto)!!
        assertEquals(at(Nov28, 21).toInstant(), suggestion.plannedDeparture.toInstant())
        assertFalse(suggestion.hasPublishedEnd)
    }

    @Test
    fun `published end is used and departure stays inside the window`() {
        val published = event("a", start = at(Nov28, 19).toString(), end = at(Nov28, 20, 15).toString())
        assertEquals(at(Nov28, 20, 15).toInstant(), suggestDeparture(published, night(), Toronto)!!.plannedDeparture.toInstant())
        assertTrue(suggestDeparture(published, night(), Toronto)!!.hasPublishedEnd)

        val late = event("b", start = at(nextDay, 1).toString(), type = "theatre")
        assertEquals(at(nextDay, 2).toInstant(), suggestDeparture(late, night(), Toronto)!!.plannedDeparture.toInstant())
    }

    @Test
    fun `Leave By is the earlier of departure and the time to reach the next Stop`() {
        val first = stop("a", at(Nov28, 18), at(Nov28, 19))
        assertEquals(at(Nov28, 19), leaveBy(first, stop("b", at(Nov28, 20), at(Nov28, 21)), legs))
        assertEquals(at(Nov28, 18, 50), leaveBy(first, stop("c", at(Nov28, 19, 10), at(Nov28, 21)), legs))
        assertEquals(at(Nov28, 19), leaveBy(first, null, legs))
    }

    @Test
    fun `Leave By ignores skipped Stops and unknown ids`() {
        val itinerary = night(
            stops = listOf(
                stop("a", at(Nov28, 18), at(Nov28, 19)),
                stop("skipped", at(Nov28, 19, 5), at(Nov28, 19, 30), status = StopStatus.Skipped),
                stop("c", at(Nov28, 21), at(Nov28, 22)),
            ),
        )
        assertEquals(at(Nov28, 19), leaveBy(itinerary, "a", legs))
        assertNull(leaveBy(itinerary, "nope", legs))
    }

    @Test
    fun `Finish needs at least one Event`() {
        assertFalse(canFinish(night()))
        assertFalse(canFinish(night(stops = listOf(stop("m", at(Nov28, 19), at(Nov28, 20), kind = StopKind.Meal)))))
        assertFalse(canFinish(night(stops = listOf(stop("e", at(Nov28, 19), at(Nov28, 20), status = StopStatus.Skipped)))))
        assertTrue(canFinish(night(stops = listOf(stop("e", at(Nov28, 19), at(Nov28, 20))))))
    }

    @Test
    fun `withStop keeps visit order and replaces the same Stop`() {
        val later = stop("b", at(Nov28, 21), at(Nov28, 22))
        val earlier = stop("a", at(Nov28, 19), at(Nov28, 20))
        val itinerary = night(stops = listOf(later)).withStop(earlier).withStop(earlier)
        assertEquals(listOf("a", "b"), itinerary.stops.map { it.id })
    }

    @Test
    fun `budget adds Event budget and selected Meals`() {
        val blocks = listOf(
            MealBlock("l", MealKind.Dinner, LocalTime.of(19, 0), BudgetBracket.Under50),
            MealBlock("s", MealKind.LateBite, LocalTime.of(22, 0), BudgetBracket.Under20, skipped = true),
        )
        val itinerary = night(blocks = blocks).copy(eventBudget = BudgetBracket.Under20)
        val summary = budgetSummary(itinerary, context(), BudgetPrices())
        assertEquals(20, summary.eventBudgetCad)
        assertEquals(50, summary.mealBudgetCad)
        assertEquals(70, summary.totalBudgetCad)
    }

    @Test
    fun `a no-limit part removes the total and the warning`() {
        val itinerary = night(stops = listOf(stop("e", at(Nov28, 19), at(Nov28, 20), ticketPriceCad = 400.0)))
            .copy(eventBudget = BudgetBracket.From100)
        val summary = budgetSummary(itinerary, context(), BudgetPrices())
        assertNull(summary.totalBudgetCad)
        assertFalse(summary.isOverBudget)
    }

    @Test
    fun `running total counts each price source`() {
        val free = event("free", cost = Cost(CostStatus.FREE))
        val reported = event("reported", cost = Cost(CostStatus.PAID))
        val shared = event("shared", cost = Cost(CostStatus.PAID))
        val unknown = event("unknown")
        val stops = listOf(
            stop("free", at(Nov28, 18), at(Nov28, 18, 30)),
            stop("reported", at(Nov28, 19), at(Nov28, 19, 30), ticketPriceCad = 12.0),
            stop("shared", at(Nov28, 20), at(Nov28, 20, 30)),
            stop("unknown", at(Nov28, 21), at(Nov28, 21, 30)),
            stop("meal", at(Nov28, 22), at(Nov28, 22, 45), kind = StopKind.Meal, sourceId = "7"),
            stop("meal-unknown", at(Nov28, 23), at(Nov28, 23, 30), kind = StopKind.Meal, sourceId = "8"),
            stop("skipped", at(Nov28, 23, 40), at(Nov28, 23, 50), status = StopStatus.Skipped, ticketPriceCad = 99.0),
        )
        val prices = BudgetPrices(
            eventsById = listOf(free, reported, shared, unknown).associateBy { it.id },
            medianPrices = mapOf(shared.showtimeKey() to 8.0, reported.showtimeKey() to 50.0),
            mealPrices = mapOf(7L to PriceRange(10, 30, false), 8L to null),
        )
        val summary = budgetSummary(night(stops = stops).copy(eventBudget = BudgetBracket.Under20), context(), prices)
        assertEquals(20.0, summary.eventsCad, 0.0)
        assertEquals(30.0, summary.mealsCad, 0.0)
        assertEquals(50.0, summary.runningTotalCad, 0.0)
        assertEquals(2, summary.unknownPriceCount)
        assertTrue(summary.hasUnknownPrices)
        assertTrue(summary.isOverBudget)
    }

    @Test
    fun `reaching the budget exactly is not over budget`() {
        val itinerary = night(stops = listOf(stop("e", at(Nov28, 19), at(Nov28, 20), ticketPriceCad = 20.0)))
            .copy(eventBudget = BudgetBracket.Under20)
        assertFalse(budgetSummary(itinerary, context(), BudgetPrices()).isOverBudget)
    }

    @Test
    fun `a free Event stays at zero even with a report`() {
        val free = event("e", cost = Cost(CostStatus.FREE))
        val itinerary = night(stops = listOf(stop("e", at(Nov28, 19), at(Nov28, 20), ticketPriceCad = 15.0)))
        val summary = budgetSummary(itinerary, context(), BudgetPrices(eventsById = mapOf("e" to free)))
        assertEquals(0.0, summary.runningTotalCad, 0.0)
    }

    private val first = stop("a", at(Nov28, 18), at(Nov28, 19))
    private val mealBlock = MealBlock("d", MealKind.Dinner, LocalTime.of(19, 30))
    private val meal = stop(
        "m",
        at(Nov28, 19, 30),
        at(Nov28, 21),
        kind = StopKind.Meal,
        sourceId = "5",
        mealBlockId = "d",
    )
    private val last = stop("z", at(nextDay, 0, 30), at(nextDay, 1, 30), district = NEXT)

    @Test
    fun `a preview does not change the Itinerary`() {
        val itinerary = night(stops = listOf(first, last))
        val preview = previewDraftEdit(itinerary, DraftEdit.ChangeDistricts(listOf(HERE)), legs, Toronto)
        assertEquals(listOf("a", "z"), itinerary.stops.map { it.id })
        assertEquals(listOf(HERE, NEXT), itinerary.districtIds)
        assertEquals(listOf("a"), preview.kept.map { it.id })
        assertEquals(StopRemovalReason.DistrictRemoved, preview.removed.single().reason)
        assertEquals(listOf(HERE), preview.proposed.districtIds)
        assertTrue(preview.needsWarning)
    }

    @Test
    fun `a narrower window removes outside Stops and their Meal Blocks`() {
        val itinerary = night(stops = listOf(first, meal, last), blocks = listOf(mealBlock))
        val preview = previewDraftEdit(
            itinerary,
            DraftEdit.ChangeWindow(LocalTime.of(18, 0), LocalTime.of(20, 0)),
            legs,
            Toronto,
        )
        assertEquals(listOf("a"), preview.kept.map { it.id })
        assertEquals(
            mapOf("m" to StopRemovalReason.MealBlockRemoved, "z" to StopRemovalReason.OutsideWindow),
            preview.removed.associate { it.stop.id to it.reason },
        )
        assertTrue(preview.proposed.mealBlocks.isEmpty())
    }

    @Test
    fun `a Stop kept by a shorter window departs at its end`() {
        val itinerary = night(stops = listOf(stop("a", at(Nov28, 18), at(Nov28, 23))))
        val preview = previewDraftEdit(
            itinerary,
            DraftEdit.ChangeWindow(LocalTime.of(18, 0), LocalTime.of(21, 0)),
            legs,
            Toronto,
        )
        assertEquals(at(Nov28, 21).toInstant(), preview.kept.single().plannedDeparture.toInstant())
    }

    @Test
    fun `a new date removes Events and moves Meals`() {
        val itinerary = night(stops = listOf(first, meal), blocks = listOf(mealBlock))
        val newDate = LocalDate.of(2026, 12, 5)
        val preview = previewDraftEdit(itinerary, DraftEdit.ChangeDate(newDate), legs, Toronto)
        assertEquals(StopRemovalReason.DateChanged, preview.removed.single().reason)
        val moved = preview.kept.single()
        assertEquals(at(newDate, 19, 30).toInstant(), moved.start.toInstant())
        assertEquals(at(newDate, 21).toInstant(), moved.plannedDeparture.toInstant())
    }

    @Test
    fun `a new date removes a Meal that would be closed`() {
        val itinerary = night(stops = listOf(meal), blocks = listOf(mealBlock))
        val preview = previewDraftEdit(
            itinerary,
            DraftEdit.ChangeDate(LocalDate.of(2026, 12, 5)),
            legs,
            Toronto,
            isMealOpen = { _, _ -> false },
        )
        assertEquals(StopRemovalReason.MealClosed, preview.removed.single().reason)
    }

    @Test
    fun `removing a Stop keeps later reachable Stops without a warning`() {
        val itinerary = night(stops = listOf(first, meal, last), blocks = listOf(mealBlock))
        val preview = previewDraftEdit(itinerary, DraftEdit.RemoveStop("m"), legs, Toronto)
        assertEquals(listOf("a", "z"), preview.kept.map { it.id })
        assertFalse(preview.needsWarning)
        assertEquals(listOf("d"), preview.proposed.mealBlocks.map { it.id })
    }

    @Test
    fun `a Stop that can no longer be reached is reported`() {
        val tight = stop("b", at(Nov28, 19, 10), at(Nov28, 20))
        val itinerary = night(stops = listOf(first, tight))
        val preview = previewDraftEdit(itinerary, DraftEdit.ChangeDistricts(listOf(HERE, NEXT)), legs, Toronto)
        assertEquals(StopRemovalReason.NoLongerReachable, preview.removed.single().reason)
    }
}
