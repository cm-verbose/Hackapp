package com.example.hackapp.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T13a: progress transitions and the JSON stored by UnderwayStore. */
class UnderwayProgressTest {
    private val trip = Itinerary(
        date = DAY.toString(),
        districts = listOf(DISTRICT),
        stops = listOf(stop("a", "10:00", "11:00"), stop("b", "12:00", "13:00"), stop("c", "14:00", "15:00", location = null)),
    )

    @Test fun startsAtTheFirstStopWithEverythingPending() {
        val p = UnderwayProgress(trip)
        assertEquals("a", p.current?.id)
        assertEquals(List(3) { StopStatus.PENDING }, p.statuses)
        assertFalse(p.atStop)
    }

    @Test fun arrivedThenLeavingConfirmsAndMovesOn() {
        val p = UnderwayProgress(trip).arrived().finishCurrent(StopStatus.CONFIRMED)
        assertEquals(1, p.index)
        assertEquals(StopStatus.CONFIRMED, p.statuses[0])
        assertFalse("atStop resets for the next Stop", p.atStop)
    }

    @Test fun leaveAndSkipNextConfirmsCurrentAndSkipsTheNext() {
        val p = UnderwayProgress(trip).finishCurrent(StopStatus.CONFIRMED).finishCurrent(StopStatus.SKIPPED)
        assertEquals(listOf(StopStatus.CONFIRMED, StopStatus.SKIPPED, StopStatus.PENDING), p.statuses)
        assertEquals("c", p.current?.id)
    }

    @Test fun finishingTheLastStopEndsTheTripAndFurtherCallsAreNoOps() {
        var p = UnderwayProgress(trip)
        repeat(3) { p = p.finishCurrent(StopStatus.CONFIRMED) }
        assertTrue(p.finished)
        assertEquals(null, p.current)
        assertEquals(p, p.finishCurrent(StopStatus.SKIPPED))
    }

    @Test fun progressSurvivesAJsonRoundTrip() {
        val json = Json { ignoreUnknownKeys = true }
        val p = UnderwayProgress(trip, index = 1, useRideHail = true, fromLat = 45.5, fromLng = -73.6)
            .copy(statuses = listOf(StopStatus.CONFIRMED, StopStatus.PENDING, StopStatus.PENDING))
        val back = json.decodeFromString<UnderwayProgress>(json.encodeToString(p))
        assertEquals(p, back)
        assertEquals(at("12:00"), back.itinerary.stops[1].start)
        assertEquals(null, back.itinerary.stops[2].location)
    }
}
