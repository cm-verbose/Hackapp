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
import com.example.hackapp.scaffold.screens.planning.ScreenPlanning
import com.example.hackapp.scaffold.screens.planning.PlanningScreenDestination

/**
 * Handles the navigation between screens which allows for dividing content
 * across different screens.
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
        composable<ScreenHomeDestination> {
            ScreenHome(padding)
        }
    }
}
