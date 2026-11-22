package com.example.hackapp.scaffold.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.hackapp.HackappApplication
import com.example.hackapp.domain.FlowViewModel
import com.example.hackapp.scaffold.screens.home.ScreenHome
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination
import com.example.hackapp.scaffold.screens.itinerary.ScreenItinerary
import com.example.hackapp.scaffold.screens.itinerary.ScreenItineraryDestination
import com.example.hackapp.scaffold.screens.login.ScreenLogin
import com.example.hackapp.scaffold.screens.login.ScreenLoginDestination
import com.example.hackapp.scaffold.screens.login.ScreenPasswordReset
import com.example.hackapp.scaffold.screens.login.ScreenRegister
import com.example.hackapp.scaffold.screens.onboarding.ScreenOnboardingBudgetAudience
import com.example.hackapp.scaffold.screens.onboarding.ScreenOnboardingInterests
import com.example.hackapp.scaffold.screens.onboarding.ScreenOnboardingProfile
import com.example.hackapp.scaffold.screens.onboarding.ScreenOnboardingVisitorType
import com.example.hackapp.scaffold.screens.passport.ScreenPassport
import com.example.hackapp.scaffold.screens.passport.ScreenPassportDestination
import com.example.hackapp.scaffold.screens.places.PlacesDestination
import com.example.hackapp.scaffold.screens.places.PlacesScreen
import com.example.hackapp.scaffold.screens.planning.PlanningScreenDestination
import com.example.hackapp.scaffold.screens.planning.ScreenItineraryReview
import com.example.hackapp.scaffold.screens.planning.ScreenPlanning
import com.example.hackapp.scaffold.screens.planning.ScreenPlanningDateTime
import com.example.hackapp.scaffold.screens.planning.ScreenPlanningDistrictPick
import com.example.hackapp.scaffold.screens.planning.ScreenPlanningMealChoice
import com.example.hackapp.scaffold.screens.planning.ScreenPlanningStopSelection
import com.example.hackapp.scaffold.screens.settings.ScreenSettings
import com.example.hackapp.scaffold.screens.underway.ScreenUnderwayArrival
import com.example.hackapp.scaffold.screens.underway.ScreenUnderwayAtStop
import com.example.hackapp.scaffold.screens.underway.ScreenUnderwayEndOfDay
import com.example.hackapp.scaffold.screens.underway.ScreenUnderwayLeg

/**
 * Handles the navigation between screens which allows for dividing content
 * across different screens.
 *
 * Flow: Login -> (Onboarding for new users) -> Home -> Planning -> Underway -> Home.
 * PlacesScreen (restaurant browse) stays registered as a route but is not the start destination.
 */
@Composable
fun AppNavigation(controller: NavHostController, padding: PaddingValues) {
    val app = LocalContext.current.applicationContext as HackappApplication
    val vm: FlowViewModel = viewModel(factory = FlowViewModel.Factory(app))

    NavHost(
        navController = controller,
        startDestination = ScreenLoginDestination,
        enterTransition = { screenEnter() },
        exitTransition = { screenExit() },
        popEnterTransition = { screenPopEnter() },
        popExitTransition = { screenPopExit() },
    ) {
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
                vm = vm,
                onPlanItinerary = {
                    vm.newDraft()
                    controller.navigate(PlanningDateTimeRoute)
                },
                onResume = {
                    val p = vm.underway.value
                    controller.navigate(
                        when {
                            p == null || p.finished -> UnderwayEndOfDayRoute
                            p.atStop -> UnderwayAtStopRoute
                            else -> UnderwayLegRoute
                        },
                    )
                },
                onSettings = { controller.navigate(SettingsRoute) },
            )
        }
        composable<ScreenItineraryDestination> {
            ScreenItinerary(controller, padding)
        }
        composable<ScreenPassportDestination> {
            ScreenPassport(controller, padding)
        }

        composable<SettingsRoute> {
            ScreenSettings(
                onBack = { controller.popBackStack() },
                onProfile = { controller.navigate(OnboardingProfileRoute) },
                onLogOff = {
                    controller.navigate(ScreenLoginDestination) {
                        popUpTo(controller.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<OnboardingVisitorTypeRoute> {
            ScreenOnboardingVisitorType(vm, { controller.popBackStack() }, { controller.navigate(OnboardingProfileRoute) })
        }
        composable<OnboardingProfileRoute> {
            ScreenOnboardingProfile(vm, { controller.popBackStack() }, { controller.navigate(OnboardingBudgetAudienceRoute) })
        }
        composable<OnboardingBudgetAudienceRoute> {
            ScreenOnboardingBudgetAudience(vm, { controller.popBackStack() }, { controller.navigate(OnboardingInterestsRoute) })
        }
        composable<OnboardingInterestsRoute> {
            ScreenOnboardingInterests(vm, { controller.popBackStack() }) {
                controller.navigate(ScreenHomeDestination) {
                    popUpTo(controller.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        composable<PlanningDateTimeRoute> {
            ScreenPlanningDateTime(vm, { controller.popBackStack() }, { controller.navigate(PlanningDistrictPickRoute) })
        }
        composable<PlanningDistrictPickRoute> {
            ScreenPlanningDistrictPick(vm, { controller.popBackStack() }, { controller.navigate(PlanningMealChoiceRoute) })
        }
        composable<PlanningMealChoiceRoute> {
            ScreenPlanningMealChoice(vm, { controller.popBackStack() }) {
                vm.resetSelection()
                controller.navigate(PlanningStopSelectionRoute)
            }
        }
        composable<PlanningStopSelectionRoute> {
            ScreenPlanningStopSelection(vm, { controller.popBackStack() }, { controller.navigate(ItineraryReviewRoute) })
        }
        composable<ItineraryReviewRoute> {
            ScreenItineraryReview(
                vm,
                onBack = { controller.popBackStack() },
                onRemoved = { controller.popBackStack() },
                onStarted = {
                    controller.navigate(UnderwayLegRoute) {
                        popUpTo(ScreenHomeDestination) { inclusive = false }
                    }
                },
            )
        }

        composable<UnderwayLegRoute> {
            ScreenUnderwayLeg(
                vm,
                onBack = { controller.popBackStack(ScreenHomeDestination, inclusive = false) },
                onArrive = { controller.navigate(UnderwayArrivalRoute) },
                onEnd = { controller.goToEndOfDay() },
            )
        }
        composable<UnderwayArrivalRoute> {
            ScreenUnderwayArrival(
                vm,
                onBack = { controller.popBackStack() },
                onAtStop = { controller.navigate(UnderwayAtStopRoute) },
                onSkipped = { finished -> controller.afterStop(finished) },
            )
        }
        composable<UnderwayAtStopRoute> {
            ScreenUnderwayAtStop(vm, onBack = { controller.popBackStack() }, onLeft = { finished -> controller.afterStop(finished) })
        }
        composable<UnderwayEndOfDayRoute> {
            ScreenUnderwayEndOfDay(vm, onDone = { controller.popBackStack(ScreenHomeDestination, inclusive = false) })
        }
    }
}

private fun NavHostController.goToEndOfDay() {
    navigate(UnderwayEndOfDayRoute) { popUpTo(UnderwayLegRoute) { inclusive = true } }
}

/** After a Stop is left or skipped: the next Leg, or the end of the day when it was the last. */
private fun NavHostController.afterStop(finished: Boolean) {
    if (finished) {
        goToEndOfDay()
    } else {
        navigate(UnderwayLegRoute) { popUpTo(UnderwayLegRoute) { inclusive = true } }
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
