package com.example.hackapp.domain

import com.example.hackapp.data.model.Geo
import com.example.hackapp.data.model.LatLng

/**
 * Sanity check for the person's position used as the first Leg's start. A fix far from the city
 * (an emulator's default location, a stale fix from another trip) would give absurd travel times,
 * so it is treated like "no location": no estimate is shown.
 */
object StartLocation {
    /** Montréal city centre. The City Pack has no centre yet; move this there when it does. */
    val CITY_CENTRE = LatLng(45.5017, -73.5673)
    const val MAX_DISTANCE_METERS = 50_000.0

    fun isUsable(fix: LatLng, centre: LatLng = CITY_CENTRE): Boolean =
        Geo.distanceMeters(fix, centre) <= MAX_DISTANCE_METERS
}
