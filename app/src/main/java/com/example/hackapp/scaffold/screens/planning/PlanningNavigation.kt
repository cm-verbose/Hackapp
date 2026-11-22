package com.example.hackapp.scaffold.screens.planning

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.example.hackapp.R
import com.example.hackapp.scaffold.navigation.ItineraryReviewRoute
import com.example.hackapp.scaffold.navigation.PlanningDateTimeRoute
import com.example.hackapp.scaffold.navigation.PlanningDistrictPickRoute
import com.example.hackapp.scaffold.navigation.PlanningMealChoiceRoute
import com.example.hackapp.scaffold.navigation.PlanningStopSelectionRoute
import com.example.hackapp.scaffold.navigation.UnderwayLegRoute
import com.example.hackapp.scaffold.navigation.flowComposable
import com.example.hackapp.scaffold.screens.RoutePlaceholder

internal const val PLANNING_STEPS = 5

/** The five Planning routes. Each page after Date and time receives its Itinerary id. */
fun NavGraphBuilder.planningGraph(controller: NavHostController) {
    flowComposable<PlanningDateTimeRoute> { entry ->
        val route = entry.toRoute<PlanningDateTimeRoute>()
        PlanningDateTimeScreen(
            itineraryId = route.itineraryId,
            onBack = { controller.popBackStack() },
            onContinue = { id -> controller.navigate(PlanningDistrictPickRoute(id)) },
        )
    }
    flowComposable<PlanningDistrictPickRoute> { entry ->
        val route = entry.toRoute<PlanningDistrictPickRoute>()
        DistrictPickScreen(
            itineraryId = route.itineraryId,
            onBack = { controller.popBackStack() },
            onContinue = { id -> controller.navigate(PlanningMealChoiceRoute(id)) },
        )
    }
    flowComposable<PlanningMealChoiceRoute> { entry ->
        val route = entry.toRoute<PlanningMealChoiceRoute>()
        RoutePlaceholder(
            title = stringResource(R.string.meal_title),
            onBack = { controller.popBackStack() },
            step = 3,
            totalSteps = PLANNING_STEPS,
            onNext = { controller.navigate(PlanningStopSelectionRoute(route.itineraryId)) },
        )
    }
    flowComposable<PlanningStopSelectionRoute> { entry ->
        val route = entry.toRoute<PlanningStopSelectionRoute>()
        RoutePlaceholder(
            title = stringResource(R.string.stop_kind_event),
            onBack = { controller.popBackStack() },
            step = 4,
            totalSteps = PLANNING_STEPS,
            onNext = { controller.navigate(ItineraryReviewRoute(route.itineraryId)) },
        )
    }
    flowComposable<ItineraryReviewRoute> {
        RoutePlaceholder(
            title = stringResource(R.string.review_title),
            onBack = { controller.popBackStack() },
            step = 5,
            totalSteps = PLANNING_STEPS,
            onNext = { controller.navigate(UnderwayLegRoute) },
        )
    }
}
