package com.example.hackapp.scaffold.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.hackapp.scaffold.screens.login.ScreenLogin
import com.example.hackapp.scaffold.screens.login.ScreenLoginDestination
import com.example.hackapp.scaffold.screens.places.PlacesDestination
import com.example.hackapp.scaffold.screens.places.PlacesScreen
import com.example.hackapp.scaffold.screens.planning.PlanningScreenDestination
import com.example.hackapp.scaffold.screens.planning.ScreenPlanning

/**
 * Handles the navigation between screens which allows for dividing content
 * across different screens.
 *
 * Flow: Login -> Planning. PlacesScreen (restaurant browse) stays registered as a route
 * but is not the start destination.
 */
@Composable
fun AppNavigation(controller: NavHostController) {
    NavHost(navController = controller, startDestination = ScreenLoginDestination) {
        composable<ScreenLoginDestination> {
            ScreenLogin(onNavigateNext = {
                controller.navigate(PlanningScreenDestination)
            })
        }
        composable<PlanningScreenDestination> {
            ScreenPlanning(onLogOff = {
                controller.navigate(ScreenLoginDestination)
            })
        }
        composable<PlacesDestination> { PlacesScreen() }
    }
}
