package com.example.hackapp

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
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

    /** The one clock for Underway: coordinator, ViewModel and pages. Demo mode replaces it here. */
    val underwayClock: UnderwayClock by lazy { SystemUnderwayClock(ActiveCityPack.timeZone) }

    /**
     * Underway: state machine, alarms, notifications and Stamp awarding. Places are looked up by id,
     * which includes Places that are no longer ACTIVE.
     */
    val underwayCoordinator: UnderwayCoordinator by lazy {
        UnderwayCoordinator(
            itineraries = itineraryRepository,
            states = createUnderwayRepository(this),
            stamps = createStampRepository(this),
            resolverFor = { itinerary ->
                val places = itinerary.stops.filter { it.kind == StopKind.Meal }.mapNotNull { stop ->
                    stop.sourceId.toLongOrNull()?.let { cityServices.places.getPlace(it) }
                }.associateBy { it.id.toString() }
                val eventTypes = itinerary.stops.filter { it.kind == StopKind.Event }.mapNotNull { stop ->
                    cityServices.events.getEvent(stop.sourceId)?.type?.let { stop.sourceId to it }
                }.toMap()
                PlaceStampIdentityResolver(placeById = { places[it] }, eventType = { eventTypes[it] })
            },
            estimator = cityServices.legs,
            clock = underwayClock,
            scheduler = AndroidUnderwayAlertScheduler(this),
            notifier = AndroidUnderwayNotifier(this, underwayClock.zone),
        )
    }

    /** The signed-in account, or null. Safe to call before Firebase is ready. */
    fun currentUid(): String? = try {
        FirebaseAuth.getInstance().currentUser?.uid
    } catch (e: Exception) {
        null
    }

    override fun onCreate() {
        super.onCreate()
        // Every time the app comes to the front counts as an app open: reschedule alerts, end a
        // forgotten Itinerary and retry unfinished Stamp awards, so Home is always up to date.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) = reconcileUnderway()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun reconcileUnderway() {
        val uid = currentUid() ?: return
        applicationScope.launch(Dispatchers.Default) {
            try {
                underwayCoordinator.onAppOpen(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Underway", "Could not reconcile Underway", e)
            }
        }
    }

    /** For short background work that must finish even if the current screen closes. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
