package com.example.hackapp.itinerary

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ListItinerariesTest {
    @Test
    fun `default one-shot list matches the observed list, newest first`() = runBlocking {
        val repository = InMemoryItineraryRepository()
        repository.saveItinerary(night(id = "old").copy(updatedAtMillis = 1))
        repository.saveItinerary(night(id = "new").copy(updatedAtMillis = 9))
        assertEquals(listOf("new", "old"), repository.listItineraries(UID).map { it.id })
        assertEquals(listOf("new", "old"), repository.listItineraries(UID, freshOnly = true).map { it.id })
    }
}

class SaveIfUnchangedTest {
    @Test
    fun `second device with a stale base is refused and the newer stops survive`() = runBlocking {
        val repository = InMemoryItineraryRepository()
        val base = night(id = "t").copy(updatedAtMillis = 1)
        assertEquals(true, repository.saveItineraryIfUnchanged(base, null))
        assertEquals(false, repository.saveItineraryIfUnchanged(base, null))
        val deviceA = base.copy(updatedAtMillis = 5)
        assertEquals(true, repository.saveItineraryIfUnchanged(deviceA, 1))
        val deviceB = base.copy(updatedAtMillis = 6, stops = emptyList())
        assertEquals(false, repository.saveItineraryIfUnchanged(deviceB, 1))
        assertEquals(5L, repository.getItinerary(UID, "t")!!.updatedAtMillis)
    }

    @Test
    fun `precondition compares exactly`() {
        assertEquals(true, preconditionHolds(null, null))
        assertEquals(true, preconditionHolds(3, 3))
        assertEquals(false, preconditionHolds(3, null))
        assertEquals(false, preconditionHolds(null, 3))
        assertEquals(false, preconditionHolds(4, 3))
    }
}
