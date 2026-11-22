package com.example.hackapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hackapp.data.local.PlacesDatabase
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.data.model.MealSlot
import com.example.hackapp.data.repository.PlaceQuery
import com.example.hackapp.data.repository.RoomPlaceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opens the prepackaged places.db: Room validates the schema on first open. */
@RunWith(AndroidJUnit4::class)
class PlacesDatabaseTest {
    private val db = PlacesDatabase.get(InstrumentationRegistry.getInstrumentation().targetContext)
    private val repo = RoomPlaceRepository(db.placeDao(), db.referenceDao())

    @Test
    fun referenceDataIsLoaded() = runBlocking {
        assertEquals(11, repo.cuisineGroups().size)
        assertEquals(4, repo.priceBrackets().size)
        assertTrue(db.placeDao().countActive() > 5000)
    }

    @Test
    fun searchNearOldPort() = runBlocking {
        val brackets = repo.priceBrackets()
        val places = repo.search(
            PlaceQuery(
                center = LatLng(45.5075, -73.5540),
                radiusMeters = 1000.0,
                venueTypes = repo.venueTypesFor(MealSlot.LUNCH),
                maxPrice = brackets.first { it.code == "UNDER_50" }.maxPrice,
            )
        )
        assertTrue(places.isNotEmpty())
    }
}
