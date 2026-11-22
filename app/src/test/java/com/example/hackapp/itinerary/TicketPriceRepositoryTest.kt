package com.example.hackapp.itinerary

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketPriceRepositoryTest {
    private val key = event("a").showtimeKey()

    @Test
    fun `median handles odd, even, empty and unsorted values`() {
        assertNull(medianOf(emptyList()))
        assertEquals(7.0, medianOf(listOf(7.0))!!, 0.0)
        assertEquals(10.0, medianOf(listOf(30.0, 5.0, 10.0))!!, 0.0)
        assertEquals(15.0, medianOf(listOf(20.0, 5.0, 10.0, 40.0))!!, 0.0)
    }

    @Test
    fun `valid range is 0 to 500 inclusive`() {
        listOf(0.0, 0.01, 499.99, 500.0).forEach { assertTrue(isValidTicketPrice(it)) }
        listOf(-0.01, 500.01, Double.NaN, Double.POSITIVE_INFINITY).forEach { assertFalse(isValidTicketPrice(it)) }
    }

    @Test
    fun `one editable report per person`() = runBlocking {
        val repository = InMemoryTicketPriceRepository()
        assertTrue(repository.saveReport("u1", key, 10.0))
        assertTrue(repository.saveReport("u1", key, 30.0))
        assertEquals(30.0, repository.getOwnReport("u1", key)!!, 0.0)
        assertEquals(TicketPriceSummary(30.0, 1), repository.getSummary(key))
    }

    @Test
    fun `an invalid report is refused and keeps the old one`() = runBlocking {
        val repository = InMemoryTicketPriceRepository()
        repository.saveReport("u1", key, 10.0)
        assertFalse(repository.saveReport("u1", key, 501.0))
        assertEquals(10.0, repository.getOwnReport("u1", key)!!, 0.0)
    }

    @Test
    fun `summary is the median across people and updates`() = runBlocking {
        val repository = InMemoryTicketPriceRepository()
        assertNull(repository.observeSummary(key).first())
        repository.saveReport("u1", key, 10.0)
        repository.saveReport("u2", key, 20.0)
        assertEquals(TicketPriceSummary(15.0, 2), repository.observeSummary(key).first())
        repository.deleteReport("u1", key)
        assertEquals(TicketPriceSummary(20.0, 1), repository.getSummary(key))
        repository.deleteReport("u2", key)
        assertNull(repository.getSummary(key))
    }

    @Test
    fun `all showtimes of a title and venue that day share a report`() = runBlocking {
        val repository = InMemoryTicketPriceRepository()
        val early = event("e", title = "Film", venue = "Cinema", start = at(Nov28, 13).toString())
        val late = event("l", title = "Film", venue = "Cinema", start = at(Nov28, 21).toString())
        val tomorrow = event("t", title = "Film", venue = "Cinema", start = at(Nov28.plusDays(1), 13).toString())
        repository.saveReport("u1", early.showtimeKey(), 12.0)
        assertEquals(12.0, repository.getOwnReport("u1", late.showtimeKey())!!, 0.0)
        assertNull(repository.getOwnReport("u1", tomorrow.showtimeKey()))
    }
}
