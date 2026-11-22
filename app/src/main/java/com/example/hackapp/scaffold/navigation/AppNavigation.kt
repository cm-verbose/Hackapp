package com.example.hackapp.scaffold.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.hackapp.R
import com.example.hackapp.profile.SessionDestination
import com.example.hackapp.scaffold.screens.RoutePlaceholder
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination
import com.example.hackapp.scaffold.screens.home.mainTabsGraph
import com.example.hackapp.scaffold.screens.login.ScreenLogin
import com.example.hackapp.scaffold.screens.login.ScreenLoginDestination
import com.example.hackapp.scaffold.screens.login.ScreenPasswordReset
import com.example.hackapp.scaffold.screens.login.ScreenRegister
import com.example.hackapp.scaffold.screens.onboarding.SessionGate
import com.example.hackapp.scaffold.screens.onboarding.onboardingGraph
import com.example.hackapp.scaffold.screens.passport.ScreenPassport
import com.example.hackapp.scaffold.screens.passport.ScreenPassportDestination
import com.example.hackapp.scaffold.screens.places.PlacesDestination
import com.example.hackapp.scaffold.screens.places.PlacesScreen
import com.example.hackapp.scaffold.screens.planning.PlanningScreenDestination
import com.example.hackapp.scaffold.screens.planning.ScreenPlanning
import com.example.hackapp.scaffold.screens.planning.planningGraph

/**
 * Handles the navigation between screens which allows for dividing content
 * across different screens.
 *
 * Flow: Session gate -> Login or onboarding -> Home -> Planning -> Underway -> Home.
 * Pages not built yet use a placeholder.
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

        placeholder<UnderwayLegRoute>(controller, R.string.leg_next_stop_label, next = UnderwayArrivalRoute)
        placeholder<UnderwayArrivalRoute>(controller, R.string.arrival_en_route, next = UnderwayAtStopRoute)
        placeholder<UnderwayAtStopRoute>(controller, R.string.at_stop_leave_by, next = UnderwayEndOfDayRoute)
        composable<UnderwayEndOfDayRoute> {
            RoutePlaceholder(
                title = stringResource(R.string.end_of_day_title),
                onBack = { controller.popBackStack() },
                onNext = { controller.popBackStack(ScreenHomeDestination, inclusive = false) },
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

private inline fun <reified T : Any> NavGraphBuilder.placeholder(
    controller: NavHostController,
    @StringRes title: Int,
    step: Int? = null,
    totalSteps: Int? = null,
    next: Any? = null,
) {
    composable<T> {
        RoutePlaceholder(
            title = stringResource(title),
            onBack = { controller.popBackStack() },
            step = step,
            totalSteps = totalSteps,
            onNext = next?.let { route -> { controller.navigate(route) } },
        )
    }
}
