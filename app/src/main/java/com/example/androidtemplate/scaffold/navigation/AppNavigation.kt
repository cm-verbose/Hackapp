package com.example.androidtemplate.scaffold.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.androidtemplate.scaffold.screens.screenA.ScreenA
import com.example.androidtemplate.scaffold.screens.screenA.ScreenADestination
import com.example.androidtemplate.scaffold.screens.screenB.ScreenB
import com.example.androidtemplate.scaffold.screens.screenB.ScreenBDestination

/**
 * Handles the navigation between screens which allows for dividing content
 * across different screens.
 */
@Composable
fun AppNavigation(controller: NavHostController) {
    NavHost(navController = controller, startDestination = ScreenADestination) {
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
