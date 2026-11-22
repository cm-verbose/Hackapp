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
import com.example.hackapp.scaffold.screens.RoutePlaceholder
import com.example.hackapp.scaffold.screens.home.ScreenHome
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination
import com.example.hackapp.scaffold.screens.itinerary.ScreenItinerary
import com.example.hackapp.scaffold.screens.itinerary.ScreenItineraryDestination
import com.example.hackapp.scaffold.screens.login.ScreenLogin
import com.example.hackapp.scaffold.screens.login.ScreenLoginDestination
import com.example.hackapp.scaffold.screens.login.ScreenPasswordReset
import com.example.hackapp.scaffold.screens.login.ScreenRegister
import com.example.hackapp.scaffold.screens.passport.ScreenPassport
import com.example.hackapp.scaffold.screens.passport.ScreenPassportDestination
import com.example.hackapp.scaffold.screens.places.PlacesDestination
import com.example.hackapp.scaffold.screens.places.PlacesScreen
import com.example.hackapp.scaffold.screens.planning.PlanningScreenDestination
import com.example.hackapp.scaffold.screens.planning.ScreenPlanning

private const val ONBOARDING_STEPS = 4
private const val PLANNING_STEPS = 5

/**
 * Handles the navigation between screens which allows for dividing content
 * across different screens.
 *
 * Flow: Login -> Home -> Planning -> Underway -> Home. Pages not built yet use a placeholder.
 * PlacesScreen (restaurant browse) stays registered as a route but is not the start destination.
 */
@Composable
fun AppNavigation(controller: NavHostController, padding: PaddingValues) {
    NavHost(navController = controller, startDestination = ScreenLoginDestination) {
        composable<ScreenLoginDestination> {
            ScreenLogin(
                onSignedIn = controller::finishSignIn,
                onRegister = { controller.navigate(RegisterRoute) },
                onForgotPassword = { controller.navigate(PasswordResetRoute) },
            )
        }
        composable<RegisterRoute> {
            ScreenRegister(
                onBack = { controller.popBackStack() },
                onSignedIn = controller::finishSignIn,
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

        composable<ScreenHomeDestination> {
            ScreenHome(
                controller = controller,
                outerPaddingValues = padding,
                onPlanItinerary = { controller.navigate(PlanningDateTimeRoute) },
            )
        }
        composable<ScreenItineraryDestination> {
            ScreenItinerary(controller, padding)
        }
        composable<ScreenPassportDestination> {
            ScreenPassport(controller, padding)
        }

        placeholder<SettingsRoute>(controller, R.string.settings_title)

        placeholder<OnboardingVisitorTypeRoute>(controller, R.string.onboarding_visitor_type_title, 1, ONBOARDING_STEPS, OnboardingProfileRoute)
        placeholder<OnboardingProfileRoute>(controller, R.string.profile_title, 2, ONBOARDING_STEPS, OnboardingBudgetAudienceRoute)
        placeholder<OnboardingBudgetAudienceRoute>(controller, R.string.budget_audience_title, 3, ONBOARDING_STEPS, OnboardingInterestsRoute)
        placeholder<OnboardingInterestsRoute>(controller, R.string.interests_title, 4, ONBOARDING_STEPS, ScreenHomeDestination)

        placeholder<PlanningDateTimeRoute>(controller, R.string.planning_new_itinerary, 1, PLANNING_STEPS, PlanningDistrictPickRoute)
        placeholder<PlanningDistrictPickRoute>(controller, R.string.district_pick_title, 2, PLANNING_STEPS, PlanningMealChoiceRoute)
        placeholder<PlanningMealChoiceRoute>(controller, R.string.meal_title, 3, PLANNING_STEPS, PlanningStopSelectionRoute)
        placeholder<PlanningStopSelectionRoute>(controller, R.string.stop_kind_event, 4, PLANNING_STEPS, ItineraryReviewRoute)
        placeholder<ItineraryReviewRoute>(controller, R.string.review_title, 5, PLANNING_STEPS, UnderwayLegRoute)

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

/** Leaves the auth screens for good: new accounts go through onboarding, others go Home. */
private fun NavHostController.finishSignIn(isNewUser: Boolean) {
    val next: Any = if (isNewUser) OnboardingVisitorTypeRoute else ScreenHomeDestination
    navigate(next) {
        popUpTo(ScreenLoginDestination) { inclusive = true }
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
