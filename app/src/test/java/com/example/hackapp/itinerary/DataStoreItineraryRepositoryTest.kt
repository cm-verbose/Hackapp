package com.example.hackapp.itinerary

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalTime

class DataStoreItineraryRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("itineraries.preferences_pb") }
    }
    private val repository by lazy { DataStoreItineraryRepository(store) }

    private val full = night(
        stops = listOf(stop("e", at(Nov28, 19), at(Nov28, 20), ticketPriceCad = 12.5)),
        blocks = listOf(MealBlock("d", MealKind.Dinner, LocalTime.of(20, 30))),
    )

    @After
    fun closeStore() = scope.cancel()

    @Test
    fun `an Itinerary survives encoding`() {
        assertEquals(full, ItineraryCodec.decode(ItineraryCodec.encode(full)))
    }

    @Test
    fun `bad records decode to null`() {
        assertNull(ItineraryCodec.decode("not json"))
        assertNull(ItineraryCodec.decode("{}"))
        val badDate = ItineraryCodec.encode(full).replace("2026-11-28", "not-a-date")
        assertNull(ItineraryCodec.decode(badDate))
    }

    @Test
    fun `save, read, replace and delete`() = runBlocking {
        repository.saveItinerary(full)
        assertEquals(full, repository.getItinerary(UID, full.id))
        val edited = full.copy(step = PlanningStep.Review, updatedAtMillis = 5)
        repository.saveItinerary(edited)
        assertEquals(listOf(edited), repository.observeItineraries(UID).first())
        repository.deleteItinerary(UID, full.id)
        assertNull(repository.getItinerary(UID, full.id))
        assertTrue(repository.observeItineraries(UID).first().isEmpty())
    }

    @Test
    fun `accounts are separate, even with similar uids`() = runBlocking {
        val mine = night(id = "x")
        val theirs = night(id = "x").copy(ownerUid = "${UID}_x")
        repository.saveItinerary(mine)
        repository.saveItinerary(theirs)
        assertEquals(listOf(mine), repository.observeItineraries(UID).first())
        assertEquals(listOf(theirs), repository.observeItineraries(theirs.ownerUid).first())
        repository.deleteItinerary(UID, "x")
        assertEquals(theirs, repository.getItinerary(theirs.ownerUid, "x"))
    }

    @Test
    fun `newest update comes first`() = runBlocking {
        repository.saveItinerary(night(id = "old").copy(updatedAtMillis = 1))
        repository.saveItinerary(night(id = "new").copy(updatedAtMillis = 9))
        assertEquals(listOf("new", "old"), repository.observeItineraries(UID).first().map { it.id })
    }

    @Test
    fun `an unreadable record does not hide the others`() = runBlocking {
        repository.saveItinerary(full)
        val uidPrefix = "itinerary_${UID.length}_${UID}_"
        store.edit { it[stringPreferencesKey(uidPrefix + "broken")] = "{ nope" }
        assertEquals(listOf(full), repository.observeItineraries(UID).first())
        assertNull(repository.getItinerary(UID, "broken"))
    }
}
