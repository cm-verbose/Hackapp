package com.example.hackapp.itinerary

import com.example.hackapp.data.Event
import com.example.hackapp.data.model.PriceRange

/** Price data needed for a running total. */
data class BudgetPrices(
    val eventsById: Map<String, Event> = emptyMap(),
    val medianPrices: Map<ShowtimeKey, Double> = emptyMap(),
    /** Price range per Place id. A missing or null value is an unknown price. */
    val mealPrices: Map<Long, PriceRange?> = emptyMap(),
)

data class BudgetSummary(
    /** Null when the Event budget has no upper limit. */
    val eventBudgetCad: Int?,
    /** Sum of selected Meal budgets. Null when any of them has no upper limit. */
    val mealBudgetCad: Int?,
    /** Event budget plus selected Meal budgets. Null when any part has no upper limit. */
    val totalBudgetCad: Int?,
    val eventsCad: Double,
    val mealsCad: Double,
    /** Stops left out of the running total, shown as "+ unknown price". */
    val unknownPriceCount: Int,
    /** Warns only. Nothing in Planning is blocked by this flag. */
    val isOverBudget: Boolean,
) {
    val runningTotalCad: Double get() = eventsCad + mealsCad
    val hasUnknownPrices: Boolean get() = unknownPriceCount > 0
}

fun budgetSummary(itinerary: Itinerary, context: PlanningContext, prices: BudgetPrices): BudgetSummary {
    val eventBudget = (itinerary.eventBudget ?: context.defaultEventBudget)?.maxPrice
    val mealLimits = itinerary.activeMealBlocks.map {
        (it.budget ?: context.defaultMealBudgets[it.kind])?.maxPrice
    }
    val mealBudget = if (mealLimits.any { it == null }) null else mealLimits.sumOf { it ?: 0 }
    val total = if (eventBudget != null && mealBudget != null) eventBudget + mealBudget else null

    var eventsCad = 0.0
    var mealsCad = 0.0
    var unknown = 0
    itinerary.activeStops.forEach { stop ->
        val cost = when (stop.kind) {
            StopKind.Event -> {
                val event = prices.eventsById[stop.sourceId]
                val median = event?.let { prices.medianPrices[it.showtimeKey()] }
                knownEventPrice(event, stop.ticketPriceCad, median)
            }
            StopKind.Meal -> stop.sourceId.toLongOrNull()?.let { prices.mealPrices[it]?.max?.toDouble() }
        }
        when {
            cost == null -> unknown++
            stop.kind == StopKind.Event -> eventsCad += cost
            else -> mealsCad += cost
        }
    }
    return BudgetSummary(
        eventBudgetCad = eventBudget,
        mealBudgetCad = mealBudget,
        totalBudgetCad = total,
        eventsCad = eventsCad,
        mealsCad = mealsCad,
        unknownPriceCount = unknown,
        isOverBudget = total != null && eventsCad + mealsCad > total,
    )
}
