package com.example.hackapp.scaffold.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlin.reflect.KClass

private const val FLOW_TRANSITION_MILLIS = 300

private val FlowRoutes: List<KClass<*>> = listOf(
    OnboardingVisitorTypeRoute::class,
    OnboardingProfileRoute::class,
    OnboardingBudgetAudienceRoute::class,
    OnboardingInterestsRoute::class,
    PlanningDateTimeRoute::class,
    PlanningDistrictPickRoute::class,
    PlanningMealChoiceRoute::class,
    PlanningStopSelectionRoute::class,
    ItineraryReviewRoute::class,
)

/** True for the step-by-step onboarding and Planning pages. */
fun NavDestination.isProgressFlow(): Boolean = FlowRoutes.any { hasRoute(it) }

fun AnimatedContentTransitionScope<NavBackStackEntry>.flowForwardEnter(): EnterTransition =
    slideIntoContainer(SlideDirection.Start, tween(FLOW_TRANSITION_MILLIS))

fun AnimatedContentTransitionScope<NavBackStackEntry>.flowForwardExit(): ExitTransition =
    slideOutOfContainer(SlideDirection.Start, tween(FLOW_TRANSITION_MILLIS))

fun AnimatedContentTransitionScope<NavBackStackEntry>.flowBackEnter(): EnterTransition =
    slideIntoContainer(SlideDirection.End, tween(FLOW_TRANSITION_MILLIS))

fun AnimatedContentTransitionScope<NavBackStackEntry>.flowBackExit(): ExitTransition =
    slideOutOfContainer(SlideDirection.End, tween(FLOW_TRANSITION_MILLIS))

/** The same forward slide, for pages that change inside one route. */
fun <S> AnimatedContentTransitionScope<S>.flowForwardTransform(): ContentTransform =
    slideIntoContainer(SlideDirection.Start, tween(FLOW_TRANSITION_MILLIS)) togetherWith
        slideOutOfContainer(SlideDirection.Start, tween(FLOW_TRANSITION_MILLIS))

/** A step that slides in the direction of progress, and back the opposite way. */
inline fun <reified T : Any> NavGraphBuilder.flowComposable(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T>(
        enterTransition = { flowForwardEnter() },
        exitTransition = { flowForwardExit() },
        popEnterTransition = { flowBackEnter() },
        popExitTransition = { flowBackExit() },
        content = content,
    )
}
