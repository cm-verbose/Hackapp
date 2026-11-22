package com.example.hackapp.scaffold.screens.planning

import com.example.hackapp.citypack.CityPack
import com.example.hackapp.data.model.Place
import com.example.hackapp.data.repository.PlaceRepository
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.PlanningChangePreview
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.itinerary.previewPlanningChange
import com.example.hackapp.services.CityServices
import com.example.hackapp.services.DistanceLegEstimator
import com.example.hackapp.services.LegEstimator
import java.time.ZoneId

/**
 * The real [PlanningChangeReconciler] for Date and time, Budget and District edits. [preview] lists
 * the Stops that no longer fit so the page can ask first; [reconcile] returns the Itinerary to save
 * once the person has confirmed (or when nothing is lost).
 */
class StopFitReconciler(
    private val estimator: LegEstimator,
    private val zone: ZoneId,
    /** Loads the Places of Meal Stops, for opening hours and budget. Empty skips those checks. */
    private val loadPlace: suspend (Long) -> Place? = { null },
) : PlanningChangeReconciler {

    override suspend fun preview(previous: Itinerary, updated: Itinerary): PlanningChangePreview {
        val places = HashMap<Long, Place>()
        for (stop in previous.stops) {
            if (stop.kind != StopKind.Meal) continue
            val id = stop.sourceId.toLongOrNull() ?: continue
            loadPlace(id)?.let { places[id] = it }
        }
        return previewPlanningChange(previous, updated, estimator, zone, places)
    }

    override suspend fun reconcile(previous: Itinerary, updated: Itinerary): Itinerary =
        preview(previous, updated).proposed
}

/** From the City Pack alone: Reachability, window and Districts, without Meal hours or budget. */
fun CityPack.stopFitReconciler(): PlanningChangeReconciler =
    StopFitReconciler(DistanceLegEstimator(legEstimator), timeZone)

/** With the Place data, so closed or over-budget Meals are found too. */
fun CityServices.stopFitReconciler(): PlanningChangeReconciler =
    StopFitReconciler(legs, cityPack.timeZone) { id -> places.getPlace(id) }
