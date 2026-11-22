package com.example.hackapp.services

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.hackapp.citypack.CityPack
import com.example.hackapp.data.local.PlacesDatabase
import com.example.hackapp.data.repository.EventRepository
import com.example.hackapp.data.repository.PlaceRepository
import com.example.hackapp.data.repository.RoomPlaceRepository
import com.example.hackapp.data.repository.SnapshotEventRepository

/**
 * Every data source and external service a screen may use for the active city.
 * To replace a provider, change only where it is built in [CityServicesFactory].
 */
class CityServices(
    val cityPack: CityPack,
    val events: EventRepository,
    placesProvider: () -> PlaceRepository,
    val legs: LegEstimator,
    val fares: FareService,
    val transit: TransitHandoff,
    val rideHail: RideHailHandoff,
) {
    /** Built on first use, because opening the database copies it from assets. */
    val places: PlaceRepository by lazy(placesProvider)
}

object CityServicesFactory {
    fun create(context: Context, pack: CityPack): CityServices = CityServices(
        cityPack = pack,
        events = SnapshotEventRepository.fromAsset(context, pack.id, pack.eventSnapshotAsset),
        placesProvider = { placesFor(context, pack) },
        legs = DistanceLegEstimator(pack.legEstimator),
        fares = FareTableService(pack.fareTable),
        transit = AndroidTransitHandoff(context, pack.transitApp),
        rideHail = UberRideHailHandoff(context, pack.rideHail),
    )

    private fun placesFor(context: Context, pack: CityPack): PlaceRepository = when (pack.id) {
        "mtl" -> PlacesDatabase.get(context).let { RoomPlaceRepository(it.placeDao(), it.referenceDao()) }
        else -> error("No Place data is configured for City Pack '${pack.id}'")
    }
}

val LocalCityServices = staticCompositionLocalOf<CityServices> {
    error("CityServices not provided")
}
