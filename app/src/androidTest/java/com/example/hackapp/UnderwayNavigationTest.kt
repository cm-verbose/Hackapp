package com.example.hackapp

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hackapp.domain.Itinerary
import com.example.hackapp.domain.Stop
import com.example.hackapp.domain.StopStatus
import com.example.hackapp.domain.UnderwayProgress
import com.example.hackapp.domain.finishCurrent
import com.example.hackapp.scaffold.navigation.AppNavigation
import com.example.hackapp.scaffold.navigation.UnderwayAtStopRoute
import com.example.hackapp.scaffold.navigation.UnderwayEndOfDayRoute
import com.example.hackapp.scaffold.navigation.UnderwayLegRoute
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.ui.theme.StopType
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T1, instrumented: End of day opens when the trip finishes, whoever finishes it, and only once.
 * WARNING: uses the app's own "trip" DataStore, so it wipes any trip in progress.
 */
@RunWith(AndroidJUnit4::class)
class UnderwayNavigationTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as HackappApplication
    private lateinit var controller: TestNavHostController

    /** A two-Stop trip, at the last Stop. */
    private fun lastStopProgress(): UnderwayProgress {
        val base = LocalDateTime.now(ZoneId.of("America/Toronto")).plusHours(2).withSecond(0).withNano(0)
        val stops = (0 until 2).map { i ->
            val start = base.plusHours(2L * i)
            Stop("nav$i", StopType.Music, "Stop $i", districtId = "ville-marie", start = start, departure = start.plusHours(1))
        }
        return UnderwayProgress(Itinerary(base.toLocalDate().toString(), listOf("ville-marie"), stops))
            .finishCurrent(StopStatus.CONFIRMED)
    }

    private fun open(vararg routes: Any) {
        compose.setContent {
            controller = TestNavHostController(context).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
            HackappTheme { AppNavigation(controller, PaddingValues()) }
        }
        compose.runOnIdle { routes.forEach { controller.navigate(it) } }
        compose.waitForIdle()
    }

    private fun endOfDayEntries() =
        controller.currentBackStack.value.count { it.destination.hasRoute(UnderwayEndOfDayRoute::class) }

    private fun waitForEndOfDay() {
        compose.waitUntil(5_000) { endOfDayEntries() > 0 }
        compose.mainClock.advanceTimeBy(1_000) // let a second navigation, if any, happen
        compose.waitForIdle()
    }

    @Before fun saveTrip() = runBlocking { app.underwayStore.save(lastStopProgress()) }

    @After fun clearTrip() = runBlocking { app.underwayStore.clear() }

    @Test fun aTripFinishedByANotificationOpensEndOfDayFromLeg() {
        open(ScreenHomeDestination, UnderwayLegRoute)
        // What the notification's Skip button does, while the Leg page is open.
        runBlocking { app.underwayStore.update { it.finishCurrent(StopStatus.SKIPPED) } }
        waitForEndOfDay()
        assertEquals(1, endOfDayEntries())
    }

    @Test fun leavingTheLastStopOpensEndOfDayExactlyOnce() {
        runBlocking { app.underwayStore.update { it.copy(atStop = true) } }
        open(ScreenHomeDestination, UnderwayAtStopRoute)
        // Both the page action and the "finished" watcher navigate; only one End of day may result.
        compose.onNodeWithText(context.getString(R.string.action_leaving)).performClick()
        waitForEndOfDay()
        assertEquals(1, endOfDayEntries())
        compose.onNodeWithText(context.getString(R.string.end_of_day_finish)).assertExists()
    }
}
