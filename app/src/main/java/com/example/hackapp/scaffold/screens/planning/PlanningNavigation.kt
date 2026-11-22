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
        PlanningMealChoiceScreen(
            itineraryId = route.itineraryId,
            onBack = { controller.popBackStack() },
            onContinue = { id -> controller.navigate(PlanningStopSelectionRoute(id)) },
            onChangeDateTime = { id -> controller.returnToDateTime(id) },
        )
    }
    planningPage<PlanningStopSelectionRoute>(controller) { entry ->
        val route = entry.toRoute<PlanningStopSelectionRoute>()
        RoutePlaceholder(
            title = stringResource(R.string.planning_stops_title),
            onBack = { controller.popBackStack() },
            step = 5,
            totalSteps = PLANNING_STEPS,
            onNext = { controller.navigate(ItineraryReviewRoute(route.itineraryId)) },
        )
    }
    planningPage<ItineraryReviewRoute>(controller) { entry ->
        val route = entry.toRoute<ItineraryReviewRoute>()
        ItineraryReviewScreen(
            itineraryId = route.itineraryId,
            onBack = { controller.popBackStack() },
            // A started Itinerary has left Planning, so its steps leave the back stack.
            onStarted = {
                controller.navigate(UnderwayLegRoute) {
                    popUpTo(ScreenHomeDestination)
                    launchSingleTop = true
                }
            },
            onBackToSelection = { id -> controller.returnToSelection(id) },
            onChangeDateTime = { id -> controller.returnToDateTimeFromReview(id) },
        )
    }
}

/** Back to the Selection entry, or Selection in place of Review when the draft was resumed here. */
private fun NavHostController.returnToSelection(itineraryId: String) {
    if (!popBackStack<PlanningStopSelectionRoute>(inclusive = false)) {
        navigate(PlanningStopSelectionRoute(itineraryId)) {
            popUpTo<ItineraryReviewRoute> { inclusive = true }
        }
    }
}

private fun NavHostController.returnToDateTimeFromReview(itineraryId: String) {
    if (!popBackStack<PlanningDateTimeRoute>(inclusive = false)) {
        navigate(PlanningDateTimeRoute(itineraryId)) {
            popUpTo<ItineraryReviewRoute> { inclusive = true }
        }
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

/**
 * Returns to the Date and time step already on the back stack, so the later steps are left again
 * from there. A draft resumed straight at a later step has no such entry, so Date and time opens
 * in place of the Meal page rather than on top of it.
 */
private fun NavHostController.returnToDateTime(itineraryId: String) {
    if (!popBackStack<PlanningDateTimeRoute>(inclusive = false)) {
        navigate(PlanningDateTimeRoute(itineraryId)) {
            popUpTo<PlanningMealChoiceRoute> { inclusive = true }
        }
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
