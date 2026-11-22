package com.example.hackapp.scaffold.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.loadDistrictMap
import com.example.hackapp.profile.LocalProfileRepository
import com.example.hackapp.profile.SessionDestination
import com.example.hackapp.profile.resolveSessionDestination
import com.example.hackapp.scaffold.navigation.OnboardingBudgetAudienceRoute
import com.example.hackapp.scaffold.navigation.OnboardingGraphRoute
import com.example.hackapp.scaffold.navigation.OnboardingInterestsRoute
import com.example.hackapp.scaffold.navigation.OnboardingProfileRoute
import com.example.hackapp.scaffold.navigation.OnboardingVisitorTypeRoute
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination
import com.example.hackapp.services.LocalCityServices
import com.example.hackapp.ui.components.ErrorState
import com.example.hackapp.ui.components.LoadingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The four onboarding routes. Their nested graph owns the shared [OnboardingViewModel]. */
fun NavGraphBuilder.onboardingGraph(controller: NavHostController) {
    navigation<OnboardingGraphRoute>(startDestination = OnboardingVisitorTypeRoute) {
        composable<OnboardingVisitorTypeRoute> { entry ->
            val viewModel = onboardingViewModel(controller, entry)
            val state by viewModel.state.collectAsState()
            OnboardingVisitorTypePage(state, onContinue = { controller.navigate(OnboardingProfileRoute) })
        }
        composable<OnboardingProfileRoute> { entry ->
            val viewModel = onboardingViewModel(controller, entry)
            val state by viewModel.state.collectAsState()
            OnboardingProfilePage(
                state = state,
                onBack = { controller.popBackStack() },
                onNameChange = viewModel::setName,
                onEmailChange = viewModel::setEmail,
                onAgeRangeChange = viewModel::setAgeRange,
                onDistrictChange = viewModel::setDistrict,
                onRetryDistricts = viewModel::retryDistricts,
                onContinue = { controller.navigate(OnboardingBudgetAudienceRoute) },
            )
        }
        composable<OnboardingBudgetAudienceRoute> { entry ->
            val viewModel = onboardingViewModel(controller, entry)
            val state by viewModel.state.collectAsState()
            OnboardingBudgetAudiencePage(
                state = state,
                onBack = { controller.popBackStack() },
                onLunchChange = viewModel::setLunchBudget,
                onDinnerChange = viewModel::setDinnerBudget,
                onEventsChange = viewModel::setEventBudget,
                onLateNightEnabledChange = viewModel::setLateNightEnabled,
                onLateNightChange = viewModel::setLateNightBudget,
                onAudienceChange = viewModel::setAudience,
                onContinue = { controller.navigate(OnboardingInterestsRoute) },
            )
        }
        composable<OnboardingInterestsRoute> { entry ->
            val viewModel = onboardingViewModel(controller, entry)
            val state by viewModel.state.collectAsState()
            LaunchedEffect(state.finished) {
                if (state.finished) {
                    controller.navigate(ScreenHomeDestination) {
                        popUpTo(OnboardingGraphRoute) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            // Stay here while saving, so the move to Home cannot be missed.
            BackHandler(enabled = state.saving) {}
            OnboardingInterestsPage(
                state = state,
                onBack = { if (!state.saving) controller.popBackStack() },
                onInterestsChange = viewModel::setInterests,
                onCuisinesChange = viewModel::setCuisineGroups,
                onRetryCuisines = viewModel::retryCuisines,
                onSkip = { viewModel.finish(skipPreferences = true) },
                onFinish = { viewModel.finish(skipPreferences = false) },
            )
        }
    }
}

@Composable
private fun onboardingViewModel(
    controller: NavHostController,
    entry: NavBackStackEntry,
): OnboardingViewModel {
    val graphEntry = remember(entry) { controller.getBackStackEntry(OnboardingGraphRoute) }
    val context = LocalContext.current.applicationContext
    val auth = LocalAuthService.current
    val profiles = LocalProfileRepository.current
    val cityServices = LocalCityServices.current
    return viewModel(
        viewModelStoreOwner = graphEntry,
        factory = OnboardingViewModel.factory(
            auth = auth,
            profiles = profiles,
            loadDistricts = {
                val pack = cityServices.cityPack
                selectableDistricts(loadDistrictMap(context, pack), pack.defaultLocale)
            },
            // The first access opens the database, which copies it from assets.
            loadCuisines = { withContext(Dispatchers.IO) { cityServices.places.cuisineGroups() } },
        ),
    )
}

/** Shown while the saved Profile is checked, so neither Welcome nor Home flashes. */
@Composable
fun SessionGate(onResolved: (SessionDestination) -> Unit) {
    val auth = LocalAuthService.current
    val profiles = LocalProfileRepository.current
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        failed = false
        val destination = try {
            resolveSessionDestination(auth, profiles)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
            return@LaunchedEffect
        }
        onResolved(destination)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (failed) ErrorState(onRetry = { attempt++ }) else LoadingState()
    }
}
