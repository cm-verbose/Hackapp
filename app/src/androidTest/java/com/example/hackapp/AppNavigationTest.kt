package com.example.hackapp

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hackapp.scaffold.navigation.AppNavigation
import com.example.hackapp.scaffold.navigation.ItineraryReviewRoute
import com.example.hackapp.scaffold.navigation.OnboardingBudgetAudienceRoute
import com.example.hackapp.scaffold.navigation.OnboardingInterestsRoute
import com.example.hackapp.scaffold.navigation.OnboardingProfileRoute
import com.example.hackapp.scaffold.navigation.OnboardingVisitorTypeRoute
import com.example.hackapp.scaffold.navigation.PlanningMealChoiceRoute
import com.example.hackapp.scaffold.navigation.UnderwayEndOfDayRoute
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
            HackappTheme { AppNavigation(controller, PaddingValues()) }
        }
        compose.runOnIdle { controller.navigate(ScreenHomeDestination) }
        compose.waitForIdle()
    }

    private fun click(resId: Int) {
        compose.onNodeWithText(context.getString(resId)).performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000) // let the screen transition finish before the next tap
    }

    @Test
    fun planActionOpensTheFirstPlanningPage() {
        click(R.string.home_plan_action)
        compose.onNodeWithText(context.getString(R.string.planning_when_title)).assertIsDisplayed()
    }

    @Test
    fun districtPickShowsTheMapInstructions() {
        click(R.string.home_plan_action)
        click(R.string.action_continue)
        compose.onNodeWithText(context.getString(R.string.district_pick_instructions)).assertIsDisplayed()
    }

    private fun open(route: Any, expectedText: Int) {
        compose.runOnIdle { controller.navigate(route) }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(expectedText)).assertIsDisplayed()
    }

    @Test fun onboardingVisitorTypeOpens() = open(OnboardingVisitorTypeRoute, R.string.onboarding_visitor_type_subtitle)

    @Test fun onboardingProfileOpens() = open(OnboardingProfileRoute, R.string.profile_prefill_note)

    @Test fun onboardingBudgetOpens() = open(OnboardingBudgetAudienceRoute, R.string.budget_subtitle)

    @Test fun onboardingInterestsOpens() = open(OnboardingInterestsRoute, R.string.interests_subtitle)

    @Test fun mealChoiceOpens() = open(PlanningMealChoiceRoute, R.string.meal_question)

    @Test fun reviewOpens() = open(ItineraryReviewRoute, R.string.review_start)

    @Test fun endOfDayOpens() = open(UnderwayEndOfDayRoute, R.string.end_of_day_finish)

    @Test
    fun settingsOpensFromHome() {
        compose.onNodeWithContentDescription(context.getString(R.string.settings_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.settings_language)).assertIsDisplayed()
    }

    @Test
    fun bottomTabsSwitchPages() {
        click(R.string.nav_passport)
        click(R.string.nav_itineraries)
        click(R.string.nav_home)
        compose.onNodeWithText(context.getString(R.string.home_plan_action)).assertIsDisplayed()
    }
}
