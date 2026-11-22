package com.example.hackapp

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.Espresso
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hackapp.auth.AuthResult
import com.example.hackapp.auth.AuthService
import com.example.hackapp.auth.AuthUser
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.ActiveCityPack
import com.example.hackapp.itinerary.InMemoryItineraryRepository
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.LocalItineraryRepository
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.itinerary.PlanningStep
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.profile.AgeRange
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.LocalProfileRepository
import com.example.hackapp.profile.Profile
import com.example.hackapp.profile.ProfileRepository
import com.example.hackapp.profile.VisitorType
import com.example.hackapp.scaffold.navigation.AppNavigation
import com.example.hackapp.scaffold.navigation.PlanningMealChoiceRoute
import com.example.hackapp.scaffold.navigation.UnderwayLegRoute
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination
import com.example.hackapp.services.CityServicesFactory
import com.example.hackapp.services.LocalCityServices
import com.example.hackapp.ui.theme.HackappTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Walks the typed route graph, which can only fail at runtime. */
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cityServices = CityServicesFactory.create(context, ActiveCityPack)
    private val itineraries = InMemoryItineraryRepository()
    private lateinit var controller: TestNavHostController

    @Before
    fun openHome() {
        compose.setContent {
            controller = TestNavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            CompositionLocalProvider(
                LocalProfileRepository provides TestProfiles,
                LocalAuthService provides SignedInAuth,
                LocalCityServices provides cityServices,
                LocalItineraryRepository provides itineraries,
            ) {
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
        awaitContinueEnabled()
        click(R.string.action_continue)
        keepBudgetAndAwaitDistrictPick()
        // District pick needs a tap on the map, and Finish needs an Event Stop, so both are seeded.
        val draftId = seedDistrictAndEventStop()
        compose.runOnIdle { controller.navigate(PlanningMealChoiceRoute(draftId)) }
        awaitContinueEnabled()
        // A first open preselects every Meal that fits the default night window (18:00 to 02:00):
        // Dinner fits, Lunch does not, and Late bite stays off because the Profile has no add-on.
        compose.onNodeWithText(context.getString(R.string.meal_dinner_at, "18:30")).assertIsSelected()
        click(R.string.action_continue)
        awaitProgressLine()
        val saved = savedDraft()
        assertEquals(true, saved.planMeals)
        assertEquals(listOf(MealKind.Dinner), saved.mealBlocks.map { it.kind })
        assertEquals(PlanningStep.Stops, saved.step)
        // One District, so the page offers only Finish, enabled by the seeded Event Stop.
        awaitEnabled(R.string.action_finish)
        click(R.string.action_finish)
        awaitText(R.string.review_start)
        assertEquals(PlanningStep.Review, savedDraft().step)
        compose.runOnIdle { controller.navigate(UnderwayLegRoute) }
        compose.waitForIdle()
        repeat(UNDERWAY_PAGES) { click(R.string.action_continue) }
        compose.onNodeWithText(context.getString(R.string.home_plan_action)).assertIsDisplayed()
    }

    /** The one draft of the test user, as last saved. */
    private fun savedDraft(): Itinerary = runBlocking {
        itineraries.observeItineraries(TEST_UID).first().single()
    }

    /** Gives the draft one District and one Event Stop inside its window. Returns its id. */
    private fun seedDistrictAndEventStop(): String = runBlocking {
        val draft = savedDraft()
        val start = draft.windowStart(ActiveCityPack.timeZone).toOffsetDateTime()
        itineraries.saveItinerary(
            draft.copy(
                districtIds = listOf(TEST_DISTRICT),
                stops = listOf(
                    Stop(
                        id = "test-stop",
                        kind = StopKind.Event,
                        sourceId = "test-event",
                        name = "Test",
                        districtId = TEST_DISTRICT,
                        latitude = 45.5075,
                        longitude = -73.5540,
                        start = start,
                        plannedDeparture = start.plusMinutes(30),
                        hasPublishedEnd = true,
                    ),
                ),
            ),
        )
        draft.id
    }

    private fun awaitProgressLine() {
        val line = context.getString(R.string.selection_progress, 5, 6, 1, 1)
        compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
            compose.onAllNodes(hasText(line)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitEnabled(resId: Int) = compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
        compose.onAllNodes(hasText(context.getString(resId)) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun systemBackLeavesPlanningForHome() {
        click(R.string.home_plan_action)
        awaitContinueEnabled()
        click(R.string.action_continue)
        keepBudgetAndAwaitDistrictPick()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.home_plan_action)).assertIsDisplayed()
    }

    private fun keepBudgetAndAwaitDistrictPick() {
        val keep = context.getString(R.string.planning_budget_keep)
        compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
            compose.onAllNodes(hasText(keep) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(keep).performClick()
        compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
            compose.onAllNodes(hasText(context.getString(R.string.district_pick_instructions)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun awaitContinueEnabled() = compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
        compose.onAllNodes(hasText(context.getString(R.string.action_continue)) and isEnabled())
            .fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun bottomTabsSwitchPages() {
        click(R.string.nav_passport)
        click(R.string.nav_itineraries)
        click(R.string.nav_home)
        compose.onNodeWithText(context.getString(R.string.home_plan_action)).assertIsDisplayed()
    }

    @Test
    fun settingsOpensFromHomeAndReturns() {
        compose.onNodeWithContentDescription(context.getString(R.string.settings_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.settings_sign_out)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        compose.onNodeWithText(context.getString(R.string.home_plan_action)).assertIsDisplayed()
    }

    private companion object {
        const val UNDERWAY_PAGES = 4
        const val TEST_DISTRICT = "ville-marie"
        const val LOAD_TIMEOUT_MILLIS = 10_000L
        const val TEST_UID = "test-user"

        /** Meal choice needs a Profile for its budget defaults. */
        val TestProfiles = object : ProfileRepository {
            override suspend fun getProfile(uid: String): Profile? = Profile(
                visitorType = VisitorType.Visitor,
                name = "Test",
                email = "test@example.com",
                ageRange = AgeRange.From25To34,
                districtOfStayId = "ville-marie",
                lunchBudget = BudgetBracket.Under20,
                dinnerBudget = BudgetBracket.Under50,
                eventBudget = BudgetBracket.Under50,
            )

            override suspend fun saveProfile(uid: String, profile: Profile) = Unit
        }

        val SignedInAuth = object : AuthService {
            override val currentUser = AuthUser(TEST_UID, null, null)
            override suspend fun signIn(email: String, password: String) = AuthResult.Success
            override suspend fun register(email: String, password: String) = AuthResult.Success
            override suspend fun sendPasswordReset(email: String) = AuthResult.Success
        }
    }
}
