package com.example.hackapp.scaffold.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.hackapp.scaffold.screens.places.PlacesDestination
import com.example.hackapp.scaffold.screens.places.PlacesScreen
import com.example.hackapp.scaffold.screens.screenA.ScreenA
import com.example.hackapp.scaffold.screens.screenA.ScreenADestination
import com.example.hackapp.scaffold.screens.screenB.ScreenB
import com.example.hackapp.scaffold.screens.screenB.ScreenBDestination

/**
 * Handles the navigation between screens which allows for dividing content
 * across different screens.
 */
@Composable
fun AppNavigation(controller: NavHostController) {
    NavHost(navController = controller, startDestination = PlacesDestination) {
        composable<PlacesDestination> { PlacesScreen() }
        composable<ScreenADestination> {
            ScreenA(onNavigateB = {
                controller.navigate(ScreenBDestination)
            })
        }
        composable<ScreenBDestination> {
            ScreenB(onNavigateA = {
                controller.navigate(ScreenADestination)
            })
        }
    }
}
