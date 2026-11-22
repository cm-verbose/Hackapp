package com.example.hackapp.scaffold.screens.itinerary

import android.content.Context
import androidx.compose.runtime.Immutable
import com.example.hackapp.citypack.CityPack
import com.example.hackapp.citypack.loadDistrictMap
import com.example.hackapp.data.repository.EventRepository
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.PlanningStep
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.itinerary.StopStatus
import com.example.hackapp.scaffold.navigation.ArchivedItineraryRoute
import com.example.hackapp.scaffold.navigation.ItineraryReviewRoute
import com.example.hackapp.scaffold.navigation.PastItineraryRoute
import com.example.hackapp.scaffold.navigation.PlanningBudgetRoute
import com.example.hackapp.scaffold.navigation.PlanningDateTimeRoute
import com.example.hackapp.scaffold.navigation.PlanningDistrictPickRoute
import com.example.hackapp.scaffold.navigation.PlanningMealChoiceRoute
import com.example.hackapp.scaffold.navigation.PlanningStopSelectionRoute
import com.example.hackapp.scaffold.navigation.UnderwayLegRoute
import com.example.hackapp.ui.theme.StopType
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

/** A person's Itineraries, grouped the way Home and the Itineraries tab show them. */
data class ItinerarySections(
    val underway: Itinerary?,
    val planning: List<Itinerary>,
    val past: List<Itinerary>,
    val archived: List<Itinerary>,
)

private val NewestFirst = compareByDescending<Itinerary> { it.date }.thenByDescending { it.updatedAtMillis }

fun List<Itinerary>.toSections() = ItinerarySections(
    underway = firstOrNull { it.phase == ItineraryPhase.Underway },
    planning = filter { it.phase == ItineraryPhase.Planning },
    past = filter { it.phase == ItineraryPhase.Completed }.sortedWith(NewestFirst),
    archived = filter { it.phase == ItineraryPhase.Archived }.sortedWith(NewestFirst),
)

/** Drafts can be deleted, including archived ones. Past Itineraries never can. */
val Itinerary.canDelete: Boolean
    get() = phase == ItineraryPhase.Planning || phase == ItineraryPhase.Archived

/** The typed route that opens this Itinerary, on its saved step while in Planning. */
fun Itinerary.openRoute(): Any = when (phase) {
    ItineraryPhase.Planning -> when (step) {
        PlanningStep.DateTime -> PlanningDateTimeRoute(id)
        PlanningStep.Budget -> PlanningBudgetRoute(id)
        PlanningStep.Districts -> PlanningDistrictPickRoute(id)
        PlanningStep.Meals -> PlanningMealChoiceRoute(id)
        PlanningStep.Stops -> PlanningStopSelectionRoute(id)
        PlanningStep.Review -> ItineraryReviewRoute(id)
    }
    ItineraryPhase.Underway -> UnderwayLegRoute
    ItineraryPhase.Completed -> PastItineraryRoute(id)
    ItineraryPhase.Archived -> ArchivedItineraryRoute(id)
}

/**
 * First-pass view of the Stamps from this Itinerary: its confirmed Stops, one per Event or Place.
 * The Passport is the source of truth once Stamp awarding exists, because a place stamped on an
 * earlier day or another location of a chain earns no new Stamp.
 */
fun Itinerary.earnedStampStops(): List<Stop> =
    stops.filter { it.status == StopStatus.Confirmed }.distinctBy { it.kind to it.sourceId }

fun eventStopType(type: String?): StopType = when (type?.lowercase()) {
    "music" -> StopType.Music
    "festival" -> StopType.Festival
    "theatre" -> StopType.Theatre
    "comedy" -> StopType.Comedy
    "exhibition" -> StopType.Exhibition
    "sport" -> StopType.Sport
    "cinema" -> StopType.Cinema
    "family" -> StopType.Family
    // StopType has no "other" entry, and this only picks a colour and an icon.
    else -> StopType.Festival
}

/** The StopType of each Stop by id. An Event that cannot be read keeps the neutral type. */
internal suspend fun EventRepository.stopTypesFor(stops: List<Stop>): Map<String, StopType> =
    stops.associate { stop ->
        stop.id to when (stop.kind) {
            StopKind.Meal -> StopType.Meal
            StopKind.Event -> eventStopType(
                try {
                    getEvent(stop.sourceId)?.type
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                },
            )
        }
    }

/** District names by id. Names only decorate titles, so a read failure yields an empty map. */
internal suspend fun loadDistrictNames(context: Context, cityPack: CityPack): Map<String, String> = try {
    loadDistrictMap(context, cityPack).districts.associate { it.id to it.name }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    emptyMap()
}

/** One Itinerary in a list. [stampCount] is null where Stamps do not apply. */
@Immutable
data class ItineraryCardUi(
    val id: String,
    val date: LocalDate,
    val districtName: String?,
    val stopCount: Int,
    val stampCount: Int?,
    val route: Any,
)

internal fun Itinerary.toCard(districtNames: Map<String, String>) = ItineraryCardUi(
    id = id,
    date = date,
    districtName = (districtIds.firstOrNull() ?: stops.firstOrNull()?.districtId)?.let(districtNames::get),
    stopCount = stops.size,
    stampCount = if (phase == ItineraryPhase.Completed) earnedStampStops().size else null,
    route = openRoute(),
)
