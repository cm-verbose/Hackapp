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
