package com.example.hackapp.services

import com.example.hackapp.citypack.LegEstimatorConfig
import com.example.hackapp.data.model.Geo
import com.example.hackapp.data.model.LatLng
import kotlin.math.ceil

enum class LegMode { WALK, TRANSIT, RIDE_HAIL }

/** [totalMinutes] is the time to reserve for a Leg: travel plus buffer. */
data class LegEstimate(
    val distanceMeters: Double,
    val travelMinutes: Int,
    val bufferMinutes: Int,
) {
    val totalMinutes: Int get() = travelMinutes + bufferMinutes
}

/** Estimates a Leg. A routing API can replace the distance-based version behind this interface. */
fun interface LegEstimator {
    fun estimate(from: LatLng, to: LatLng, mode: LegMode): LegEstimate

    /** Longest walk, in minutes, that still counts as the default Leg inside one District. */
    val maxWalkMinutes: Int get() = DEFAULT_MAX_WALK_MINUTES
}

const val DEFAULT_MAX_WALK_MINUTES = 20

class DistanceLegEstimator(private val config: LegEstimatorConfig) : LegEstimator {
    override val maxWalkMinutes: Int get() = config.maxWalkMinutes


    override fun estimate(from: LatLng, to: LatLng, mode: LegMode): LegEstimate {
        val routeMeters = Geo.distanceMeters(from, to) * config.routeDetourFactor
        val (metersPerMinute, buffer) = when (mode) {
            LegMode.WALK -> config.walkMetersPerMinute to config.walkBufferMinutes
            LegMode.TRANSIT -> config.transitMetersPerMinute to config.transitBufferMinutes
            LegMode.RIDE_HAIL -> config.rideHailMetersPerMinute to config.rideHailBufferMinutes
        }
        return LegEstimate(
            distanceMeters = routeMeters,
            travelMinutes = ceil(routeMeters / metersPerMinute).toInt(),
            bufferMinutes = buffer,
        )
    }
}
