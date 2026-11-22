package com.example.hackapp

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hackapp.profile.LocalProfileRepository
import com.example.hackapp.profile.Profile
import com.example.hackapp.profile.ProfileRepository
import com.example.hackapp.scaffold.navigation.AppNavigation
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination
import com.example.hackapp.ui.theme.HackappTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Walks the typed route graph, which can only fail at runtime. */
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var controller: TestNavHostController

    @Before
    fun openHome() {
        compose.setContent {
            controller = TestNavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            CompositionLocalProvider(LocalProfileRepository provides NoProfiles) {
                HackappTheme { AppNavigation(controller, PaddingValues()) }
            }
        }
        compose.runOnIdle { controller.navigate(ScreenHomeDestination) }
        compose.waitForIdle()
    }

    private fun click(resId: Int) = compose.onNodeWithText(context.getString(resId)).performClick()

    @Test
    fun planningAndUnderwayFlowReturnsHome() {
        click(R.string.home_plan_action)
        repeat(PLANNING_AND_UNDERWAY_PAGES) { click(R.string.action_continue) }
        compose.onNodeWithText(context.getString(R.string.home_plan_action)).assertIsDisplayed()
    }

    @Test
    fun bottomTabsSwitchPages() {
        click(R.string.nav_passport)
        click(R.string.nav_itineraries)
        click(R.string.nav_home)
        compose.onNodeWithText(context.getString(R.string.home_plan_action)).assertIsDisplayed()
    }

    private companion object {
        const val PLANNING_AND_UNDERWAY_PAGES = 9

        val NoProfiles = object : ProfileRepository {
            override suspend fun getProfile(uid: String): Profile? = null
            override suspend fun saveProfile(uid: String, profile: Profile) = Unit
        }
    }
}
