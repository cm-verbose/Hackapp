package com.example.hackapp.scaffold.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.hackapp.scaffold.navigation.ArchivedItineraryRoute
import com.example.hackapp.scaffold.navigation.PastItineraryRoute
import com.example.hackapp.scaffold.navigation.PlanningDateTimeRoute
import com.example.hackapp.scaffold.navigation.SettingsRoute
import com.example.hackapp.scaffold.screens.itinerary.ScreenArchivedItinerary
import com.example.hackapp.scaffold.screens.itinerary.ScreenItinerary
import com.example.hackapp.scaffold.screens.itinerary.ScreenItineraryDestination
import com.example.hackapp.scaffold.screens.itinerary.ScreenPastItinerary
import com.example.hackapp.scaffold.screens.login.ScreenLoginDestination
import com.example.hackapp.scaffold.screens.settings.ScreenSettings

/** Home, Itineraries and Settings. Passport stays in AppNavigation and is owned separately. */
fun NavGraphBuilder.mainTabsGraph(controller: NavHostController, padding: PaddingValues) {
    composable<ScreenHomeDestination> {
        ScreenHome(
            controller = controller,
            outerPaddingValues = padding,
            onPlanItinerary = { controller.navigate(PlanningDateTimeRoute()) },
            onOpenItinerary = { route -> controller.navigate(route) },
            onOpenSettings = { controller.navigate(SettingsRoute) { launchSingleTop = true } },
        )
    }
    composable<ScreenItineraryDestination> {
        ScreenItinerary(
            controller = controller,
            outerPaddingValues = padding,
            onPlanItinerary = { controller.navigate(PlanningDateTimeRoute()) },
            onOpenItinerary = { route -> controller.navigate(route) },
        )
    }
    composable<PastItineraryRoute> { entry ->
        ScreenPastItinerary(
            itineraryId = entry.toRoute<PastItineraryRoute>().itineraryId,
            onBack = { controller.popBackStack() },
        )
    }
    composable<ArchivedItineraryRoute> { entry ->
        val itineraryId = entry.toRoute<ArchivedItineraryRoute>().itineraryId
        ScreenArchivedItinerary(
            itineraryId = itineraryId,
            onBack = { controller.popBackStack() },
            onChooseNewDate = { controller.navigate(PlanningDateTimeRoute(itineraryId)) },
        )
    }
    composable<SettingsRoute> {
        ScreenSettings(
            onBack = { controller.popBackStack() },
            onSignedOut = {
                controller.navigate(ScreenLoginDestination) {
                    popUpTo(controller.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }
}
