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

/** One Meal Stop in the running total. [priceCad] is null when unknown. [budgetCad] is null with no bracket or no top. */
data class MealAmount(
    val stopId: String,
    val mealBlockId: String?,
    val kind: MealKind?,
    val priceCad: Double?,
    val budgetCad: Int?,
)

data class BudgetSummary(
    /** Null when the Event budget has no upper limit. */
    val eventBudgetCad: Int?,
    /** Sum of selected Meal budgets. Null when any bracket has no upper limit. A Meal with no bracket adds nothing. */
    val mealBudgetCad: Int?,
    /** Event budget plus selected Meal budgets. Null when any part has no upper limit. */
    val totalBudgetCad: Int?,
    val eventsCad: Double,
    val mealsCad: Double,
    /** Stops left out of the running total, shown as "+ unknown price". */
    val unknownPriceCount: Int,
    /** Warns only. Nothing in Planning is blocked by this flag. */
    val isOverBudget: Boolean,
    /** Each active Meal Stop with its own amount, in visit order. Empty by default for older callers. */
    val meals: List<MealAmount> = emptyList(),
) {
    val runningTotalCad: Double get() = eventsCad + mealsCad
    val hasUnknownPrices: Boolean get() = unknownPriceCount > 0
}

fun budgetSummary(itinerary: Itinerary, context: PlanningContext, prices: BudgetPrices): BudgetSummary {
    val eventBudget = (itinerary.eventBudget ?: context.defaultEventBudget)?.maxPrice
    // A Meal with no bracket at all (for example Late bite with its add-on off) has no limit to add,
    // but its price still counts below. A bracket with no top ("$100+") makes the limit unlimited.
    val mealBrackets = itinerary.activeMealBlocks.mapNotNull { it.budget ?: itinerary.mealBudget(it.kind, context) }
    val mealBudget = if (mealBrackets.any { it.maxPrice == null }) null else mealBrackets.sumOf { it.maxPrice ?: 0 }
    val total = if (eventBudget != null && mealBudget != null) eventBudget + mealBudget else null

    var eventsCad = 0.0
    var mealsCad = 0.0
    var unknown = 0
    val mealAmounts = mutableListOf<MealAmount>()
    itinerary.activeStops.forEach { stop ->
        val cost = when (stop.kind) {
            StopKind.Event -> {
                val event = prices.eventsById[stop.sourceId]
                val median = event?.let { prices.medianPrices[it.showtimeKey()] }
                knownEventPrice(event, stop.ticketPriceCad, median)
            }
            StopKind.Meal -> stop.sourceId.toLongOrNull()?.let { prices.mealPrices[it]?.max?.toDouble() }.also { price ->
                val block = itinerary.mealBlocks.firstOrNull { it.id == stop.mealBlockId }
                val bracket = block?.let { it.budget ?: itinerary.mealBudget(it.kind, context) }
                mealAmounts += MealAmount(stop.id, block?.id, block?.kind, price, bracket?.maxPrice)
            }
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
        meals = mealAmounts,
    )
}
