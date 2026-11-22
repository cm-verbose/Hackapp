package com.example.hackapp

import android.app.Application
import com.example.hackapp.citypack.ActiveCityPack
import com.example.hackapp.itinerary.ItineraryRepository
import com.example.hackapp.itinerary.createItineraryRepository
import com.example.hackapp.profile.ProfileRepository
import com.example.hackapp.profile.createProfileRepository
import com.example.hackapp.services.CityServices
import com.example.hackapp.services.CityServicesFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.passport.PlaceStampIdentityResolver
import com.example.hackapp.passport.createStampRepository
import com.example.hackapp.underway.SystemUnderwayClock
import com.example.hackapp.underway.UnderwayClock
import com.example.hackapp.underway.UnderwayCoordinator
import com.example.hackapp.underway.android.AndroidUnderwayAlertScheduler
import com.example.hackapp.underway.android.AndroidUnderwayNotifier
import com.example.hackapp.underway.createUnderwayRepository

/** Owns the single [CityServices] container for this build's City Pack. */
class HackappApplication : Application() {
    val cityServices: CityServices by lazy { CityServicesFactory.create(this, ActiveCityPack) }

    /** Lives outside [CityServices] because a Profile belongs to the account, not to a city. */
    val profileRepository: ProfileRepository by lazy { createProfileRepository(this) }

    val itineraryRepository: ItineraryRepository by lazy { createItineraryRepository(this) }

    /**
     * Underway: state machine, alarms, notifications and Stamp awarding. Meal Places are looked up by id,
     * which includes Places that are no longer ACTIVE.
     */
    /** The one clock for Underway: coordinator, ViewModel and pages. Demo mode replaces it here. */
    val underwayClock: UnderwayClock by lazy { SystemUnderwayClock(ActiveCityPack.timeZone) }

    val underwayCoordinator: UnderwayCoordinator by lazy {
        UnderwayCoordinator(
            itineraries = itineraryRepository,
            states = createUnderwayRepository(this),
            stamps = createStampRepository(this),
            resolverFor = { itinerary ->
                val places = itinerary.stops.filter { it.kind == StopKind.Meal }.mapNotNull { stop ->
                    stop.sourceId.toLongOrNull()?.let { cityServices.places.getPlace(it) }
                }.associateBy { it.id.toString() }
                PlaceStampIdentityResolver(placeById = { places[it] })
            },
            estimator = cityServices.legs,
            clock = underwayClock,
            scheduler = AndroidUnderwayAlertScheduler(this),
            notifier = AndroidUnderwayNotifier(this),
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Process start counts as an app open: reschedule alerts and retry unfinished Stamp awards.
        applicationScope.launch(Dispatchers.Default) { underwayCoordinator.onBoot() }
    }

    /** For short background work that must finish even if the current screen closes. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
