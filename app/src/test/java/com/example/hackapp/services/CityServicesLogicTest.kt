package com.example.hackapp.services

import com.example.hackapp.citypack.LegEstimatorConfig
import com.example.hackapp.data.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CityServicesLogicTest {

    private val estimator = DistanceLegEstimator(LegEstimatorConfig())
    private val from = LatLng(45.5017, -73.5673)
    private val to = LatLng(45.5230, -73.5810)

    @Test
    fun `leg total is travel plus mode buffer`() {
        val walk = estimator.estimate(from, to, LegMode.WALK)
        assertEquals(walk.travelMinutes + 5, walk.totalMinutes)
        val transit = estimator.estimate(from, to, LegMode.TRANSIT)
        assertEquals(transit.travelMinutes + 10, transit.totalMinutes)
        assertTrue(transit.travelMinutes < walk.travelMinutes)
    }

    @Test
    fun `leg with no distance still reserves its buffer`() {
        val leg = estimator.estimate(from, from, LegMode.RIDE_HAIL)
        assertEquals(0, leg.travelMinutes)
        assertEquals(8, leg.totalMinutes)
    }

    @Test
    fun `transit directions url targets the destination`() {
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=45.523,-73.581&travelmode=transit",
            HandoffUrls.transitDirections(to),
        )
    }

    @Test
    fun `walking directions url asks for walking`() {
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=45.523,-73.581&travelmode=walking",
            HandoffUrls.walkingDirections(to),
        )
    }

    @Test
    fun `uber url encodes the drop-off name`() {
        val url = HandoffUrls.uberDropoff(HandoffDestination("Bistro du coin & café", to))
        assertTrue(url.contains("dropoff%5Blatitude%5D=45.523"))
        assertTrue(url.contains("dropoff%5Bnickname%5D=Bistro%20du%20coin%20%26%20caf%C3%A9"))
    }
}
