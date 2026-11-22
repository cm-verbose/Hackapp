package com.example.hackapp.scaffold.navigation

import com.example.hackapp.scaffold.screens.share.ItineraryShareScreen
import androidx.navigation.toRoute
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.hackapp.profile.SessionDestination
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination
import com.example.hackapp.scaffold.screens.home.mainTabsGraph
import com.example.hackapp.scaffold.screens.login.ScreenLogin
import com.example.hackapp.scaffold.screens.login.ScreenLoginDestination
import com.example.hackapp.scaffold.screens.login.ScreenPasswordReset
import com.example.hackapp.scaffold.screens.login.ScreenRegister
import com.example.hackapp.scaffold.screens.onboarding.SessionGate
import com.example.hackapp.scaffold.screens.onboarding.onboardingGraph
import com.example.hackapp.scaffold.screens.passport.ScreenPassport
import com.example.hackapp.scaffold.screens.share.StampShareScreen
import com.example.hackapp.scaffold.screens.passport.ScreenPassportDestination
import com.example.hackapp.scaffold.screens.places.PlacesDestination
import com.example.hackapp.scaffold.screens.places.PlacesScreen
import com.example.hackapp.scaffold.screens.planning.PlanningScreenDestination
import com.example.hackapp.scaffold.screens.planning.ScreenPlanning
import com.example.hackapp.scaffold.screens.planning.planningGraph
import com.example.hackapp.scaffold.screens.underway.UnderwayScreen

/**
 * Handles the navigation between screens which allows for dividing content
 * across different screens.
 *
 * Flow: Session gate -> Login or onboarding -> Home -> Planning -> Underway -> Home.
 * PlacesScreen (restaurant browse) stays registered as a route but is not the start destination.
 */
@Composable
fun AppNavigation(controller: NavHostController, padding: PaddingValues) {
    NavHost(navController = controller, startDestination = SessionGateRoute) {
        composable<SessionGateRoute> {
            SessionGate(onResolved = controller::openSessionDestination)
        }
        composable<ScreenLoginDestination> {
            ScreenLogin(
                onSignedIn = { controller.finishSignIn() },
                onRegister = { controller.navigate(RegisterRoute) },
                onForgotPassword = { controller.navigate(PasswordResetRoute) },
            )
        }
        composable<RegisterRoute> {
            ScreenRegister(
                onBack = { controller.popBackStack() },
                onSignedIn = { controller.finishSignIn() },
            )
        }
        composable<PasswordResetRoute> {
            ScreenPasswordReset(onBack = { controller.popBackStack() })
        }
        composable<PlanningScreenDestination> {
            Box(Modifier.padding(padding)) {
                ScreenPlanning(onLogOff = { controller.navigate(ScreenLoginDestination) })
            }
        }
        composable<PlacesDestination> {
            Box(Modifier.padding(padding)) { PlacesScreen() }
        }

        mainTabsGraph(controller, padding)
        composable<ScreenPassportDestination> {
            ScreenPassport(controller, padding)
        }

        onboardingGraph(controller)
        planningGraph(controller)

        composable<UnderwayLegRoute> { entry ->
            UnderwayScreen(
                showStart = entry.toRoute<UnderwayLegRoute>().started,
                onExitToHome = { controller.exitToHome() },
                onShare = { id -> controller.navigate(ShareItineraryRoute(id)) },
                onOpenPassport = { controller.exitToPassport() },
            )
        }
        composable<ShareStampRoute> { entry ->
            StampShareScreen(
                stampKey = entry.toRoute<ShareStampRoute>().stampKey,
                onBack = { controller.popBackStack() },
            )
        }
        composable<ShareItineraryRoute> { entry ->
            ItineraryShareScreen(
                itineraryId = entry.toRoute<ShareItineraryRoute>().itineraryId,
                onBack = { controller.popBackStack() },
            )
        }
    }
}

/** Leaves the auth screens for good. The gate then picks onboarding or Home from the Profile. */
private fun NavHostController.finishSignIn() {
    navigate(SessionGateRoute) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.openSessionDestination(destination: SessionDestination) {
    val next: Any = when (destination) {
        SessionDestination.Welcome -> ScreenLoginDestination
        SessionDestination.Onboarding -> OnboardingGraphRoute
        SessionDestination.Home -> ScreenHomeDestination
    }
    navigate(next) {
        popUpTo(SessionGateRoute) { inclusive = true }
        launchSingleTop = true
    }
}

/** After the last Stop: Home underneath, the Passport on top, so Back from the Passport goes Home. */
private fun NavHostController.exitToPassport() {
    exitToHome()
    navigate(ScreenPassportDestination) { launchSingleTop = true }
}

/** Leaves Underway without ending the Itinerary. Home's Underway card resumes it. */
private fun NavHostController.exitToHome() {
    if (!popBackStack(ScreenHomeDestination, inclusive = false)) {
        navigate(ScreenHomeDestination) {
            popUpTo(graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
}
