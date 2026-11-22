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
import com.example.hackapp.scaffold.navigation.PlanningDateTimeRoute
import com.example.hackapp.scaffold.navigation.PlanningDistrictPickRoute
import com.example.hackapp.scaffold.navigation.PlanningMealChoiceRoute
import com.example.hackapp.scaffold.navigation.PlanningStopSelectionRoute
import com.example.hackapp.scaffold.navigation.ShareItineraryRoute
import com.example.hackapp.scaffold.navigation.UnderwayLegRoute
import com.example.hackapp.scaffold.navigation.flowComposable
import com.example.hackapp.scaffold.screens.RoutePlaceholder
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination

internal const val PLANNING_STEPS = 5

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
            onContinue = { id -> controller.navigate(PlanningDistrictPickRoute(id)) },
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
            onContinue = { id -> controller.navigate(PlanningStopSelectionRoute(id, intro = true)) },
            onChangeDateTime = { id -> controller.returnToDateTime(id) },
        )
    }
    planningPage<PlanningStopSelectionRoute>(controller) { entry ->
        val route = entry.toRoute<PlanningStopSelectionRoute>()
        PlanningStopSelectionScreen(
            itineraryId = route.itineraryId,
            returnGap = route.districtId?.let { SelectionGap(route.afterStopId, it) },
            showIntro = route.intro,
            onLeave = { id -> controller.returnToMealChoice(id) },
            onReview = { id -> controller.navigate(ItineraryReviewRoute(id)) },
            onHome = { controller.backToHome() },
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
            onBackToSelection = { target -> controller.returnToSelection(target) },
            onChangeDateTime = { id -> controller.returnToDateTimeFromReview(id) },
            onShare = { id -> controller.navigate(ShareItineraryRoute(id)) },
        )
    }
}

/**
 * Opens Selection on the gap a removed Stop left. The earlier Selection entry is replaced, because
 * its route holds no gap. Without one, Selection takes Review's place in a resumed draft.
 */
private fun NavHostController.returnToSelection(target: ReviewNavigation.BackToSelection) {
    if (!popBackStack<PlanningStopSelectionRoute>(inclusive = true)) {
        popBackStack<ItineraryReviewRoute>(inclusive = true)
    }
    navigate(PlanningStopSelectionRoute(target.itineraryId, target.afterStopId, target.districtId))
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

/**
 * Returns to the Meal choice step on the back stack. A draft resumed straight at selection has no
 * such entry, so Meal choice opens in place of the selection page.
 */
private fun NavHostController.returnToMealChoice(itineraryId: String) {
    if (!popBackStack<PlanningMealChoiceRoute>(inclusive = false)) {
        navigate(PlanningMealChoiceRoute(itineraryId)) {
            popUpTo<PlanningStopSelectionRoute> { inclusive = true }
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
