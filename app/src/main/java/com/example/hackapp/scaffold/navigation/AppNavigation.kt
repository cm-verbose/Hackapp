package com.example.hackapp.scaffold.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.hackapp.scaffold.screens.home.ScreenHome
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination
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
 * Flow: Login -> Home. PlacesScreen (restaurant browse) stays registered as a route
 * but is not the start destination.
 */
@Composable
fun AppNavigation(controller: NavHostController, padding: PaddingValues) {
    NavHost(navController = controller, startDestination = ScreenLoginDestination) {
        composable<ScreenLoginDestination> {
            ScreenLogin(onNavigateNext = {
                controller.navigate(ScreenHomeDestination)
            })
        }
        composable<PlanningScreenDestination> {
            ScreenPlanning(onLogOff = {
                controller.navigate(ScreenLoginDestination)
            })
        }
        composable<PlacesDestination> { PlacesScreen() }
        composable<ScreenHomeDestination> {
            ScreenHome(padding)
        }
    }
}
