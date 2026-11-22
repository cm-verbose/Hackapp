package com.example.hackapp.itinerary

import com.example.hackapp.data.Event
import com.example.hackapp.services.LegEstimator
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import com.example.hackapp.itinerary.canFinish as canFinishItinerary
import com.example.hackapp.itinerary.isEligible as isEventEligible
import com.example.hackapp.itinerary.leaveBy as leaveByTime
import com.example.hackapp.itinerary.mealOptions as findMealOptions

/** Entry point for the Planning screens. Its logic is pure and returns typed results. */
class PlanningEngine(private val estimator: LegEstimator) {
    fun isEligible(event: Event, context: PlanningContext): Boolean = isEventEligible(event, context)

    fun reachableEvents(search: EventSearch): List<EventCard> = rankedEventCards(search, estimator)

    fun groupedEvents(search: EventSearch): List<EventHourGroup> =
        groupEventCards(rankedEventCards(search, estimator))

    fun mealOptions(search: MealSearch): MealOptions = findMealOptions(search, estimator)

    fun mealBlocks(itinerary: Itinerary, context: PlanningContext): List<MealBlock> =
        defaultMealBlocks(itinerary, context)

    fun leaveBy(itinerary: Itinerary, stopId: String): OffsetDateTime? =
        leaveByTime(itinerary, stopId, estimator)

    fun budget(itinerary: Itinerary, context: PlanningContext, prices: BudgetPrices): BudgetSummary =
        budgetSummary(itinerary, context, prices)

    fun previewEdit(
        itinerary: Itinerary,
        edit: DraftEdit,
        context: PlanningContext,
        isMealOpen: (Stop, ZonedDateTime) -> Boolean = { _, _ -> true },
    ): DraftEditPreview = previewDraftEdit(itinerary, edit, estimator, context.zone, isMealOpen)

    fun canFinish(itinerary: Itinerary): Boolean = canFinishItinerary(itinerary)
}
