package com.example.hackapp.scaffold.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination
import com.example.hackapp.scaffold.screens.itinerary.ScreenItineraryDestination
import com.example.hackapp.scaffold.screens.passport.ScreenPassportDestination
import com.example.hackapp.ui.motion.HackappMotion

/*
 * Screen transitions.
 *  - Between the three bottom-bar tabs: a short shared axis (small slide toward the tab + fade).
 *  - Everything else (a step deeper in a flow): the new page slides in while the old one drifts away
 *    (parallax); Back plays it in reverse.
 */

private typealias Scope = AnimatedContentTransitionScope<NavBackStackEntry>

private val tabs = listOf(ScreenHomeDestination::class, ScreenItineraryDestination::class, ScreenPassportDestination::class)

private fun NavBackStackEntry.tabIndex() = tabs.indexOfFirst { destination.hasRoute(it) }

/** +1 / -1 when moving between two tabs, 0 when this is not a tab-to-tab move. */
private fun Scope.tabDirection(): Int {
    val from = initialState.tabIndex()
    val to = targetState.tabIndex()
    return if (from < 0 || to < 0 || from == to) 0 else if (to > from) 1 else -1
}

private fun <T> curve(duration: Int, delay: Int = 0) = tween<T>(duration, delay, HackappMotion.Decelerate)

private val Slide = tween<IntOffset>(HackappMotion.Medium, easing = HackappMotion.Decelerate)

fun Scope.screenEnter(): EnterTransition {
    val dir = tabDirection()
    return if (dir == 0) {
        slideIntoContainer(SlideDirection.Start, Slide) + fadeIn(curve(HackappMotion.Medium))
    } else {
        slideIntoContainer(if (dir > 0) SlideDirection.Start else SlideDirection.End, Slide, initialOffset = { it / 6 }) +
            fadeIn(curve(HackappMotion.Medium, delay = 60))
    }
}

fun Scope.screenExit(): ExitTransition {
    val dir = tabDirection()
    return if (dir == 0) {
        slideOutOfContainer(SlideDirection.Start, Slide, targetOffset = { it / 4 }) + fadeOut(curve(HackappMotion.Medium))
    } else {
        slideOutOfContainer(if (dir > 0) SlideDirection.Start else SlideDirection.End, Slide, targetOffset = { it / 6 }) +
            fadeOut(curve(HackappMotion.Short))
    }
}

fun Scope.screenPopEnter(): EnterTransition =
    if (tabDirection() == 0) {
        slideIntoContainer(SlideDirection.End, Slide, initialOffset = { it / 4 }) + fadeIn(curve(HackappMotion.Medium))
    } else {
        screenEnter()
    }

fun Scope.screenPopExit(): ExitTransition =
    if (tabDirection() == 0) {
        slideOutOfContainer(SlideDirection.End, Slide) + fadeOut(curve(HackappMotion.Medium))
    } else {
        screenExit()
    }
