package com.example.hackapp

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.Espresso
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
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
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.LocalItineraryRepository
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.itinerary.OutingKind
import com.example.hackapp.itinerary.PlanningStep
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.passport.InMemoryStampRepository
import com.example.hackapp.passport.PlaceStampIdentityResolver
import com.example.hackapp.profile.AgeRange
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.LocalProfileRepository
import com.example.hackapp.profile.Profile
import com.example.hackapp.profile.ProfileRepository
import com.example.hackapp.profile.VisitorType
import com.example.hackapp.scaffold.navigation.AppNavigation
import com.example.hackapp.scaffold.navigation.ItineraryReviewRoute
import com.example.hackapp.scaffold.navigation.PlanningMealChoiceRoute
import com.example.hackapp.scaffold.navigation.UnderwayLegRoute
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination
import com.example.hackapp.scaffold.screens.passport.ScreenPassportDestination
import com.example.hackapp.scaffold.screens.underway.LocalUnderwayCoordinator
import com.example.hackapp.services.CityServicesFactory
import com.example.hackapp.services.LocalCityServices
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.underway.FakeUnderwayAlertScheduler
import com.example.hackapp.underway.FakeUnderwayNotifier
import com.example.hackapp.underway.InMemoryUnderwayRepository
import com.example.hackapp.underway.SystemUnderwayClock
import com.example.hackapp.underway.UnderwayCoordinator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/** Walks the typed route graph, which can only fail at runtime. */
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cityServices = CityServicesFactory.create(context, ActiveCityPack)
    private val itineraries = InMemoryItineraryRepository()

    /** The real coordinator over the test's Itineraries, with no real alarms or notifications. */
    private val coordinator = UnderwayCoordinator(
        itineraries = itineraries,
        states = InMemoryUnderwayRepository(),
        stamps = InMemoryStampRepository(),
        resolverFor = { PlaceStampIdentityResolver(placeById = { null }) },
        estimator = cityServices.legs,
        clock = SystemUnderwayClock(ActiveCityPack.timeZone),
        scheduler = FakeUnderwayAlertScheduler(),
        notifier = FakeUnderwayNotifier(),
    )
    private lateinit var controller: TestNavHostController

    @Before
    fun openHome() {
        // Start asks for this permission, and the system dialog would cover the app under test.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        compose.setContent {
            controller = TestNavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            CompositionLocalProvider(
                LocalProfileRepository provides TestProfiles,
                LocalAuthService provides SignedInAuth,
                LocalCityServices provides cityServices,
                LocalItineraryRepository provides itineraries,
                LocalUnderwayCoordinator provides coordinator,
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
        awaitDistrictPick()
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
        compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
            compose.onAllNodes(hasText(context.getString(R.string.review_start))).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(PlanningStep.Review, savedDraft().step)
        // This draft was not started, so the Underway route has nothing to show and returns Home.
        compose.runOnIdle { controller.navigate(UnderwayLegRoute()) }
        awaitHome()
        compose.onNodeWithText(context.getString(R.string.home_plan_action)).assertIsDisplayed()
    }

    private fun awaitHome() = compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
        controller.currentBackStackEntry?.destination?.hasRoute<ScreenHomeDestination>() == true
    }

    @Test
    fun startFromReviewOpensUnderwayAboveHome() {
        startSeededDraft()
        compose.onNodeWithText(firstLegLabel()).assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(controller.previousBackStackEntry?.destination?.hasRoute<ScreenHomeDestination>() == true)
        }
        assertEquals(ItineraryPhase.Underway, savedDraft().phase)
    }

    @Test
    fun systemBackKeepsItineraryUnderwayAndHomeResumesIt() {
        startSeededDraft()
        compose.onNodeWithText(firstLegLabel()).assertIsDisplayed()
        Espresso.pressBack()
        awaitHome()
        assertEquals(ItineraryPhase.Underway, savedDraft().phase)
        compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
            compose.onAllNodes(hasText(context.getString(R.string.home_resume_itinerary)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        click(R.string.home_resume_itinerary)
        awaitUnderwayPage()
        compose.onNodeWithText(firstLegLabel()).assertIsDisplayed()
    }

    private fun firstLegLabel() = context.getString(R.string.leg_stop_progress, 1, 1)

    private fun awaitUnderwayPage() {
        compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
            controller.currentBackStackEntry?.destination?.hasRoute<UnderwayLegRoute>() == true
        }
        compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
            compose.onAllNodes(hasText(firstLegLabel())).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Saves a draft for today and taps Start on its Review page. */
    private fun startSeededDraft() {
        val zone = ActiveCityPack.timeZone
        val today = LocalDate.now(zone)
        // A night window ends after midnight, so it is still open at any time of the test day.
        val start = today.atTime(18, 0).atZone(zone).toOffsetDateTime()
        val draft = Itinerary(
            id = "start-draft",
            ownerUid = TEST_UID,
            cityId = ActiveCityPack.id,
            step = PlanningStep.Review,
            date = today,
            outingKind = OutingKind.Night,
            startTime = LocalTime.of(18, 0),
            endTime = LocalTime.of(2, 0),
            districtIds = listOf(TEST_DISTRICT),
            stops = listOf(
                Stop(
                    id = "start-stop",
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
            createdAtMillis = 0,
            updatedAtMillis = 0,
        )
        runBlocking { itineraries.saveItinerary(draft) }
        compose.runOnIdle { controller.navigate(ItineraryReviewRoute(draft.id)) }
        awaitEnabled(R.string.review_start)
        click(R.string.review_start)
        awaitUnderwayPage()
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
        awaitDistrictPick()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.home_plan_action)).assertIsDisplayed()
    }

    /** Date and time leads straight to District pick: Planning no longer has a Budget step. */
    private fun awaitDistrictPick() {
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
    fun passportAfterLastStopKeepsBottomTabsWorking() {
        startSeededDraft()
        // Skipping the only Stop ends the day; End of day then opens the Passport.
        // A touch does not reach this button under the test rule; the semantics click does.
        compose.onNodeWithText(context.getString(R.string.action_skip_stop))
            .performSemanticsAction(SemanticsActions.OnClick)
        val confirm = context.getString(R.string.underway_skip_confirm)
        compose.waitUntil(LOAD_TIMEOUT_MILLIS) { compose.onAllNodes(hasText(confirm)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText(confirm)).onLast().performClick()
        compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
            compose.onAllNodes(hasText(context.getString(R.string.end_of_day_open_passport)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        click(R.string.end_of_day_open_passport)
        compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
            controller.currentBackStackEntry?.destination?.hasRoute<ScreenPassportDestination>() == true
        }
        compose.waitForIdle()
        click(R.string.nav_home)
        awaitHome()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.home_plan_action)).assertIsDisplayed()
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
