package com.example.hackapp.itinerary

import com.example.hackapp.citypack.LegEstimatorConfig
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.services.DistanceLegEstimator
import com.example.hackapp.services.LegEstimate
import com.example.hackapp.services.LegEstimator
import com.example.hackapp.services.LegMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Walking costs [walk] minutes of travel and 5 of buffer, transit [transit] and 10, ride hail 8. */
private fun modeLegs(walk: Int, transit: Int = 20) = object : LegEstimator {
    override fun estimate(from: LatLng, to: LatLng, mode: LegMode) = when (mode) {
        LegMode.WALK -> LegEstimate(500.0, walk, 5)
        LegMode.TRANSIT -> LegEstimate(500.0, transit, 10)
        LegMode.RIDE_HAIL -> LegEstimate(500.0, 8, 8)
    }
}

class DefaultLegModeTest {
    private val a = LatLng(45.5, -73.6)
    private val b = LatLng(45.51, -73.6)

    private fun mode(from: String?, to: String?, est: LegEstimator) = defaultLegMode(from, to, a, b, est)

    @Test
    fun `one District walks and another takes transit`() {
        assertEquals(LegMode.WALK, mode(HERE, HERE, modeLegs(walk = 10)))
        assertEquals(LegMode.TRANSIT, mode(HERE, NEXT, modeLegs(walk = 10)))
        assertEquals(LegMode.TRANSIT, mode(null, HERE, modeLegs(walk = 10)))
        assertEquals(LegMode.TRANSIT, mode(HERE, null, modeLegs(walk = 10)))
    }

    @Test
    fun `a walk longer than 20 minutes uses transit, and 20 still walks`() {
        assertEquals(LegMode.WALK, mode(HERE, HERE, modeLegs(walk = 20)))
        assertEquals(LegMode.TRANSIT, mode(HERE, HERE, modeLegs(walk = 21)))
    }

    @Test
    fun `the limit is the City Pack's config value`() {
        val est = DistanceLegEstimator(LegEstimatorConfig(maxWalkMinutes = 5))
        assertEquals(5, est.maxWalkMinutes)
        assertEquals(20, DistanceLegEstimator(LegEstimatorConfig()).maxWalkMinutes)
        // About 1.1 km straight: far more than 5 minutes on foot.
        assertEquals(LegMode.TRANSIT, mode(HERE, HERE, est))
    }

    @Test
    fun `the default estimate is the one Planning and Underway both show`() {
        val est = modeLegs(walk = 6, transit = 20)
        assertEquals(6, est.defaultEstimate(HERE, HERE, a, b).travelMinutes)
        assertEquals(20, est.defaultEstimate(HERE, NEXT, a, b).travelMinutes)
        // A walk is never slower than the transit it replaces, so it cannot add unreachable warnings.
        assertTrue(est.defaultEstimate(HERE, HERE, a, b).totalMinutes <= est.estimate(a, b, LegMode.TRANSIT).totalMinutes)
    }

    @Test
    fun `Leave By uses the default Leg`() {
        val est = modeLegs(walk = 5, transit = 20)
        val first = stop("a", at(Nov28, 19), at(Nov28, 22))
        val sameDistrict = stop("b", at(Nov28, 21), at(Nov28, 22), district = HERE)
        val otherDistrict = stop("c", at(Nov28, 21), at(Nov28, 22), district = NEXT)
        // Walking: 5 + 5 buffer = 10 minutes. Transit: 20 + 10 = 30 minutes.
        assertEquals(at(Nov28, 20, 50), leaveBy(first, sameDistrict, est))
        assertEquals(at(Nov28, 20, 30), leaveBy(first, otherDistrict, est))
    }

    @Test
    fun `a Meal in the same District shows the walking minutes that Planning uses`() {
        val est = modeLegs(walk = 7, transit = 20)
        val walking = com.example.hackapp.scaffold.screens.planning.mealTravelMode(HERE, HERE, a, b, est)
        assertEquals(LegMode.WALK, walking)
        assertEquals(7, est.estimate(a, b, walking).travelMinutes)
        assertEquals(LegMode.TRANSIT, com.example.hackapp.scaffold.screens.planning.mealTravelMode(HERE, NEXT, a, b, est))
    }
}
