package com.example.hackapp.scaffold.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.hackapp.R
import com.example.hackapp.scaffold.navigation.PlanningDateTimeRoute
import com.example.hackapp.scaffold.navigation.SettingsRoute
import com.example.hackapp.scaffold.screens.RoutePlaceholder
import com.example.hackapp.scaffold.screens.itinerary.ScreenItinerary
import com.example.hackapp.scaffold.screens.itinerary.ScreenItineraryDestination
import com.example.hackapp.scaffold.screens.login.ScreenLoginDestination

/** Home, Itineraries and Settings. Passport stays in AppNavigation and is owned separately. */
fun NavGraphBuilder.mainTabsGraph(controller: NavHostController, padding: PaddingValues) {
    composable<ScreenHomeDestination> {
        ScreenHome(
            controller = controller,
            outerPaddingValues = padding,
            onPlanItinerary = { controller.navigate(PlanningDateTimeRoute()) },
            onLogOff = {
                controller.navigate(ScreenLoginDestination) {
                    popUpTo(controller.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }
    composable<ScreenItineraryDestination> {
        ScreenItinerary(controller, padding)
    }
    composable<SettingsRoute> {
        RoutePlaceholder(
            title = stringResource(R.string.settings_title),
            onBack = { controller.popBackStack() },
        )
    }
}
