package com.example.hackapp.itinerary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItinerarySharingTest {
    private val e1 = stop("e1", at(Nov28, 19), at(Nov28, 20), ticketPriceCad = 30.0)
    private val e2 = stop("e2", at(Nov28, 21), at(Nov28, 22))

    @Test
    fun `a shared draft arrives as the receiver's own draft without personal data`() {
        val draft = night(stops = listOf(e1, e2)).copy(step = PlanningStep.Stops)
        val payload = draft.toSharePayload()
        assertNotNull(payload)
        val copy = importSharedItinerary(decodeSharedItinerary(payload!!)!!, "friend", "mtl", instant(Nov28, 12))!!
        assertNotEquals(draft.id, copy.id)
        assertEquals("friend", copy.ownerUid)
        assertEquals(ItineraryPhase.Planning, copy.phase)
        assertEquals(PlanningStep.Stops, copy.step)
        assertTrue(copy.stops.all { it.ticketPriceCad == null && it.status == StopStatus.Planned })
    }

    @Test
    fun `an Underway Itinerary joins at the next Stop and never copies a confirmed one`() {
        val underway = night(stops = listOf(e1.copy(status = StopStatus.Confirmed), e2), phase = ItineraryPhase.Underway)
        val shared = decodeSharedItinerary(underway.toSharePayload()!!)!!
        val copy = importSharedItinerary(shared, "friend", "mtl", instant(Nov28, 20, 30))!!
        assertEquals(PlanningStep.Review, copy.step)
        assertEquals(listOf(StopStatus.Skipped, StopStatus.Planned), copy.stops.map { it.status })
        assertNull(importSharedItinerary(shared, "friend", "mtl", instant(Nov28, 23)))
    }

    @Test
    fun `past Itineraries, other cities and junk are refused`() {
        assertNull(night(phase = ItineraryPhase.Completed).toSharePayload())
        val shared = decodeSharedItinerary(night(stops = listOf(e1)).toSharePayload()!!)!!
        assertNull(importSharedItinerary(shared, "friend", "tor", instant(Nov28, 12)))
        assertNull(decodeSharedItinerary("{not json"))
    }
}
