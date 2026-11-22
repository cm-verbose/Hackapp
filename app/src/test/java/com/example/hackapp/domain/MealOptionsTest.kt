package com.example.hackapp.domain

import com.example.hackapp.data.model.CuisineGroup
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.data.model.LocalizedText
import com.example.hackapp.data.model.MealSlot
import com.example.hackapp.data.model.VenueType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T13a: Meal options (`ItineraryPlanner.mealOptions`).
 * Spec: screen-list Planning 4 (bars count as Meals from 17:00, hidden for family-friendly and
 * no-alcohol), data design 7 (price) and 6.3 (cuisine chips rank, never hide).
 * Bars: user decision 2026-11-22 (ADR 0003): from 17:00 including dinner, adults-only audience.
 */
class MealOptionsTest {
    private val evening = MealBlock(MealSlot.EVENING, at("22:00"))

    private fun typesQueried(repo: FakePlaceRepository) = repo.queries.single().venueTypes.orEmpty()

    @Test fun barsAreOfferedInTheEveningForAdultsOnly() = runBlocking {
        val repo = FakePlaceRepository(listOf(place(1, VenueType.BAR)))
        val found = planner(places = repo).mealOptions(input(), DISTRICT, CENTER, evening, emptySet())
        assertTrue(VenueType.BAR in typesQueried(repo))
        assertEquals(listOf(1L), found.map { it.id })
    }

    @Test fun barsAreHiddenForFamilyFriendlyAndNoAlcohol() = runBlocking {
        for (audience in listOf(Audience.FAMILY_FRIENDLY, Audience.NO_ALCOHOL)) {
            val repo = FakePlaceRepository(listOf(place(1, VenueType.BAR)))
            val found = planner(places = repo).mealOptions(input(audience = audience), DISTRICT, CENTER, evening, emptySet())
            assertFalse("$audience", VenueType.BAR in typesQueried(repo))
            assertTrue("$audience", found.isEmpty())
        }
    }

    @Test fun barsAreHiddenBefore17h() = runBlocking {
        val repo = FakePlaceRepository(listOf(place(1, VenueType.BAR)))
        planner(places = repo).mealOptions(input(), DISTRICT, CENTER, MealBlock(MealSlot.EVENING, at("16:00")), emptySet())
        assertFalse(VenueType.BAR in typesQueried(repo))
    }

    /**
     * EXPECTED TO FAIL until T22 (user decision 2026-11-22, ADR 0003: bars from 17:00 including
     * dinner, adults only). The fake slot table mirrors the shipped reference.yaml (BAR only in
     * EVENING). If T22 changes the table instead of the planner, update the fake's DINNER slot
     * to the new table and keep this assertion.
     */
    @Test fun barsAreOfferedAtDinnerFrom17hForAdults() = runBlocking {
        val repo = FakePlaceRepository(listOf(place(1, VenueType.BAR), place(2)))
        val found = planner(places = repo).mealOptions(input(), DISTRICT, CENTER, MealBlock(MealSlot.DINNER, at("18:30")), emptySet())
        assertTrue(VenueType.BAR in typesQueried(repo))
        assertTrue(found.any { it.id == 1L })
    }

    @Test fun budgetAndMealTimeArePassedToTheSearch() = runBlocking {
        val repo = FakePlaceRepository(emptyList())
        val lunch = MealBlock(MealSlot.LUNCH, at("12:00"))
        planner(places = repo).mealOptions(input(budgetMax = 50), DISTRICT, CENTER, lunch, emptySet())
        val q = repo.queries.single()
        assertEquals(50, q.maxPrice)
        assertEquals(lunch.mealStart.atZone(ZONE), q.openAt)
        assertEquals(setOf(VenueType.RESTAURANT, VenueType.FAST_FOOD), q.venueTypes)
    }

    @Test fun withoutAnAnchorOnlyPlacesInTheDistrictAreKept() = runBlocking {
        val repo = FakePlaceRepository(listOf(place(1), place(2, borough = "plateau-mont-royal")))
        val found = planner(places = repo).mealOptions(input(), DISTRICT, anchor = null, block = MealBlock(MealSlot.LUNCH, at("12:00")), profileCuisines = emptySet())
        assertEquals(listOf(1L), found.map { it.id })
    }

    @Test fun withoutAnAnchorTheSearchIsCentredOnTheDistrictPlaces() = runBlocking {
        val repo = FakePlaceRepository(listOf(place(1, location = LatLng(45.0, -73.0)), place(2, location = LatLng(46.0, -74.0))))
        planner(places = repo).mealOptions(input(), DISTRICT, null, MealBlock(MealSlot.LUNCH, at("12:00")), emptySet())
        assertEquals(LatLng(45.5, -73.5), repo.queries.single().center)
    }

    @Test fun cuisineInterestsRankFirstButNeverHide() = runBlocking {
        val groups = listOf(CuisineGroup("italian", "", LocalizedText("Italien", "Italian"), setOf("italian", "pizza"), venueType = null))
        val repo = FakePlaceRepository(
            listOf(place(1, cuisines = listOf("thai"), distance = 50.0), place(2, cuisines = listOf("pizza"), distance = 400.0)),
            groups = groups,
        )
        val found = planner(places = repo).mealOptions(input(), DISTRICT, CENTER, MealBlock(MealSlot.LUNCH, at("12:00")), setOf("italian"))
        assertEquals(listOf(2L, 1L), found.map { it.id })
    }
}
