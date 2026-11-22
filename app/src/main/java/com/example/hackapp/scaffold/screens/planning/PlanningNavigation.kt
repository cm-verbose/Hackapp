package com.example.hackapp.scaffold.screens.planning

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.hackapp.R
import com.example.hackapp.scaffold.navigation.ItineraryReviewRoute
import com.example.hackapp.scaffold.navigation.PlanningDateTimeRoute
import com.example.hackapp.scaffold.navigation.PlanningDistrictPickRoute
import com.example.hackapp.scaffold.navigation.PlanningMealChoiceRoute
import com.example.hackapp.scaffold.navigation.PlanningStopSelectionRoute
import com.example.hackapp.scaffold.navigation.UnderwayLegRoute
import com.example.hackapp.scaffold.screens.RoutePlaceholder

private const val PLANNING_STEPS = 5

/** Used until Date and time creates a real draft. */
private const val PLACEHOLDER_ITINERARY_ID = "draft"

/** The five Planning routes. Each page after Date and time receives its Itinerary id. */
fun NavGraphBuilder.planningGraph(controller: NavHostController) {
    composable<PlanningDateTimeRoute> { entry ->
        val route = entry.toRoute<PlanningDateTimeRoute>()
        RoutePlaceholder(
            title = stringResource(R.string.planning_new_itinerary),
            onBack = { controller.popBackStack() },
            step = 1,
            totalSteps = PLANNING_STEPS,
            onNext = {
                controller.navigate(PlanningDistrictPickRoute(route.itineraryId ?: PLACEHOLDER_ITINERARY_ID))
            },
        )
    }
    composable<PlanningDistrictPickRoute> { entry ->
        val route = entry.toRoute<PlanningDistrictPickRoute>()
        RoutePlaceholder(
            title = stringResource(R.string.district_pick_title),
            onBack = { controller.popBackStack() },
            step = 2,
            totalSteps = PLANNING_STEPS,
            onNext = { controller.navigate(PlanningMealChoiceRoute(route.itineraryId)) },
        )
    }
    composable<PlanningMealChoiceRoute> { entry ->
        val route = entry.toRoute<PlanningMealChoiceRoute>()
        RoutePlaceholder(
            title = stringResource(R.string.meal_title),
            onBack = { controller.popBackStack() },
            step = 3,
            totalSteps = PLANNING_STEPS,
            onNext = { controller.navigate(PlanningStopSelectionRoute(route.itineraryId)) },
        )
    }
    composable<PlanningStopSelectionRoute> { entry ->
        val route = entry.toRoute<PlanningStopSelectionRoute>()
        RoutePlaceholder(
            title = stringResource(R.string.stop_kind_event),
            onBack = { controller.popBackStack() },
            step = 4,
            totalSteps = PLANNING_STEPS,
            onNext = { controller.navigate(ItineraryReviewRoute(route.itineraryId)) },
        )
    }
    composable<ItineraryReviewRoute> {
        RoutePlaceholder(
            title = stringResource(R.string.review_title),
            onBack = { controller.popBackStack() },
            step = 5,
            totalSteps = PLANNING_STEPS,
            onNext = { controller.navigate(UnderwayLegRoute) },
        )
    }
}
