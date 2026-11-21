package com.example.hackapp.scaffold.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.hackapp.scaffold.screens.login.ScreenLogin
import com.example.hackapp.scaffold.screens.login.ScreenLoginDestination
import com.example.hackapp.scaffold.screens.planning.PlanningScreen
import com.example.hackapp.scaffold.screens.planning.ScreenBDestination

/**
 * Handles the navigation between screens which allows for dividing content
 * across different screens.
 */
@Composable
fun AppNavigation(controller: NavHostController) {
    NavHost(navController = controller, startDestination = ScreenLoginDestination) {
        composable<ScreenLoginDestination> {
            ScreenLogin(onNavigateNext = {
                controller.navigate(ScreenBDestination)
            })
        }
        composable<ScreenBDestination> {
            PlanningScreen(onNavigateA = {
                controller.navigate(ScreenLoginDestination)
            })
        }
    }
}
