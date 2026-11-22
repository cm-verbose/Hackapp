package com.example.hackapp.scaffold.screens.planning

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.example.hackapp.R
import com.example.hackapp.scaffold.navigation.ItineraryReviewRoute
import com.example.hackapp.scaffold.navigation.PlanningBudgetEditRoute
import com.example.hackapp.scaffold.navigation.PlanningBudgetRoute
import com.example.hackapp.scaffold.navigation.PlanningDateTimeRoute
import com.example.hackapp.scaffold.navigation.PlanningDistrictPickRoute
import com.example.hackapp.scaffold.navigation.PlanningMealChoiceRoute
import com.example.hackapp.scaffold.navigation.PlanningStopSelectionRoute
import com.example.hackapp.scaffold.navigation.UnderwayLegRoute
import com.example.hackapp.scaffold.navigation.flowComposable
import com.example.hackapp.scaffold.screens.RoutePlaceholder
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination

internal const val PLANNING_STEPS = 6

/**
 * The Planning routes. The app bar arrow returns to the previous step, while the system Back
 * action leaves Planning for Home. Drafts are saved, so they can be resumed from there.
 */
fun NavGraphBuilder.planningGraph(controller: NavHostController) {
    planningPage<PlanningDateTimeRoute>(controller) { entry ->
        val route = entry.toRoute<PlanningDateTimeRoute>()
        PlanningDateTimeScreen(
            itineraryId = route.itineraryId,
            onBack = { controller.popBackStack() },
            onContinue = { id -> controller.navigate(PlanningBudgetRoute(id)) },
        )
    }
    planningPage<PlanningBudgetRoute>(controller) { entry ->
        val route = entry.toRoute<PlanningBudgetRoute>()
        PlanningBudgetChoiceScreen(
            itineraryId = route.itineraryId,
            onBack = { controller.popBackStack() },
            onKept = { id -> controller.navigate(PlanningDistrictPickRoute(id)) },
            onChange = { controller.navigate(PlanningBudgetEditRoute(route.itineraryId)) },
        )
    }
    planningPage<PlanningBudgetEditRoute>(controller) { entry ->
        val route = entry.toRoute<PlanningBudgetEditRoute>()
        PlanningBudgetEditScreen(
            itineraryId = route.itineraryId,
            onBack = { controller.popBackStack() },
            onSaved = { id -> controller.navigate(PlanningDistrictPickRoute(id)) },
        )
    }
    planningPage<PlanningDistrictPickRoute>(controller) { entry ->
        val route = entry.toRoute<PlanningDistrictPickRoute>()
        DistrictPickScreen(
            itineraryId = route.itineraryId,
            onBack = { controller.popBackStack() },
            onContinue = { id -> controller.navigate(PlanningMealChoiceRoute(id)) },
        )
    }
    planningPage<PlanningMealChoiceRoute>(controller) { entry ->
        val route = entry.toRoute<PlanningMealChoiceRoute>()
        RoutePlaceholder(
            title = stringResource(R.string.meal_title),
            onBack = { controller.popBackStack() },
            step = 4,
            totalSteps = PLANNING_STEPS,
            onNext = { controller.navigate(PlanningStopSelectionRoute(route.itineraryId)) },
        )
    }
    planningPage<PlanningStopSelectionRoute>(controller) { entry ->
        val route = entry.toRoute<PlanningStopSelectionRoute>()
        RoutePlaceholder(
            title = stringResource(R.string.stop_kind_event),
            onBack = { controller.popBackStack() },
            step = 5,
            totalSteps = PLANNING_STEPS,
            onNext = { controller.navigate(ItineraryReviewRoute(route.itineraryId)) },
        )
    }
    planningPage<ItineraryReviewRoute>(controller) {
        RoutePlaceholder(
            title = stringResource(R.string.review_title),
            onBack = { controller.popBackStack() },
            step = 6,
            totalSteps = PLANNING_STEPS,
            onNext = { controller.navigate(UnderwayLegRoute) },
        )
    }
}

private inline fun <reified T : Any> NavGraphBuilder.planningPage(
    controller: NavHostController,
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    flowComposable<T> { entry ->
        BackHandler { controller.backToHome() }
        content(entry)
    }
}

private fun NavHostController.backToHome() {
    if (!popBackStack(ScreenHomeDestination, inclusive = false)) {
        navigate(ScreenHomeDestination) {
            popUpTo(graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
}
