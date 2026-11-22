package com.example.hackapp.domain

import com.example.hackapp.data.model.LatLng
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T10c: a start position far from the city is ignored (no absurd Leg estimate). */
class StartLocationTest {
    @Test fun aFixInMontrealIsUsed() {
        assertTrue(StartLocation.isUsable(LatLng(45.5075, -73.5540))) // Old Montréal
        assertTrue(StartLocation.isUsable(LatLng(45.4473, -73.7491))) // Trudeau airport, about 20 km
    }

    @Test fun theEmulatorDefaultFixIsIgnored() {
        assertFalse(StartLocation.isUsable(LatLng(37.4220, -122.0841))) // Mountain View, CA
    }

    @Test fun aFixJustOutsideTheLimitIsIgnored() {
        assertFalse(StartLocation.isUsable(LatLng(46.00, -73.5673))) // about 55 km north
        assertTrue(StartLocation.isUsable(LatLng(45.90, -73.5673))) // about 44 km north
    }
}
