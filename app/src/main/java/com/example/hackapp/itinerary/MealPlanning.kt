package com.example.hackapp.itinerary

import com.example.hackapp.data.model.CuisineGroup
import com.example.hackapp.data.model.Geo
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.data.model.Place
import com.example.hackapp.data.model.VenueType
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.hidesBars
import com.example.hackapp.services.LegEstimate
import com.example.hackapp.services.LegEstimator
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

val MealKind.defaultStart: LocalTime
    get() = when (this) {
        MealKind.Lunch -> LocalTime.of(12, 0)
        MealKind.Dinner -> LocalTime.of(18, 30)
        MealKind.LateBite -> LocalTime.of(22, 0)
    }

private val BarsOpenFrom: LocalTime = LocalTime.of(17, 0)
private const val OPEN_CHECK_STEP_SECONDS = 15 * 60L

/** True when the whole Meal Block fits inside the Itinerary's time window. */
fun MealBlock.fitsWindow(itinerary: Itinerary, zone: ZoneId): Boolean =
    itinerary.planningWindow(zone).encloses(interval(itinerary, zone))

/** The default Meal Blocks whose whole reserved time is inside the window. */
fun defaultMealBlocks(itinerary: Itinerary, context: PlanningContext): List<MealBlock> =
    MealKind.entries
        .map { kind ->
            MealBlock(
                id = "meal-${kind.name.lowercase(Locale.ROOT)}",
                kind = kind,
                start = kind.defaultStart,
                budget = context.defaultMealBudgets[kind],
            )
        }
        .filter { it.fitsWindow(itinerary, context.zone) }

data class MealSearch(
    val itinerary: Itinerary,
    val block: MealBlock,
    val places: List<Place>,
    val context: PlanningContext,
    val currentDistrictId: String,
    val nextDistrictId: String? = null,
    /** Reference point when no Stop precedes the block. */
    val origin: LatLng? = null,
    /** From `PlaceRepository.cuisineGroups()`. */
    val cuisineCatalog: List<CuisineGroup> = emptyList(),
    /** Unknown hours stay visible. Replaceable because JVM tests cannot parse Android JSON. */
    val isOpenAt: (Place, ZonedDateTime) -> Boolean = { place, time ->
        place.openingHours?.isOpenAt(time) ?: true
    },
)

data class MealOption(
    val place: Place,
    val inNextDistrict: Boolean,
    /** Straight-line distance from the previous Stop, when there is a reference point. */
    val distanceMeters: Double?,
    val leg: LegEstimate?,
    /** Counted cost. Null when unknown or when the range has no top ("$100+"). */
    val topPriceCad: Int?,
    val isPriceUnknown: Boolean,
    val matchesCuisine: Boolean,
)

/** The **Here** and **In [next District]** tabs of the Meal list. */
data class MealOptions(val here: List<MealOption>, val nextDistrict: List<MealOption>)

/** The last active Stop that starts before this block. */
fun previousStopBefore(itinerary: Itinerary, block: MealBlock, zone: ZoneId): Stop? {
    val blockStart = block.interval(itinerary, zone).start
    return itinerary.activeStops.lastOrNull { it.start.toInstant().isBefore(blockStart) }
}

fun mealOptions(search: MealSearch, estimator: LegEstimator): MealOptions {
    val zone = search.context.zone
    val interval = search.block.interval(search.itinerary, zone)
    val previous = previousStopBefore(search.itinerary, search.block, zone)
    val origin = previous?.latLng ?: search.origin
    val budget = search.block.budget ?: search.context.defaultMealBudgets[search.block.kind]
    val barsAllowed = barsAllowed(search)
    val preferred = search.cuisineCatalog.filter { it.code in search.context.cuisineGroups }

    val candidates = search.places.filter { place ->
        (place.venueType != VenueType.BAR || barsAllowed) &&
            fitsMealBudget(place, budget) &&
            isOpenDuring(place, interval, zone, search.isOpenAt)
    }

    fun optionsIn(districtId: String, inNext: Boolean): List<MealOption> = candidates
        .filter { it.borough == districtId }
        .mapNotNull { place ->
            val leg = origin?.let { estimator.transit(it, place.location) }
            if (inNext && previous != null && leg != null) {
                val arrival = previous.plannedDeparture.toInstant().plusSeconds(leg.totalMinutes * 60L)
                if (arrival.isAfter(interval.start)) return@mapNotNull null
            }
            MealOption(
                place = place,
                inNextDistrict = inNext,
                distanceMeters = origin?.let { Geo.distanceMeters(it, place.location) },
                leg = leg,
                topPriceCad = place.price?.max,
                isPriceUnknown = place.price == null,
                matchesCuisine = matchesCuisine(place, preferred),
            )
        }
        .sortedWith(MealOptionRanking)

    val next = search.nextDistrictId?.takeIf { it != search.currentDistrictId }
    return MealOptions(
        here = optionsIn(search.currentDistrictId, inNext = false),
        nextDistrict = next?.let { optionsIn(it, inNext = true) }.orEmpty(),
    )
}

internal val MealOptionRanking: Comparator<MealOption> = compareBy<MealOption> { it.isPriceUnknown }
    .thenBy { !it.matchesCuisine }
    .thenBy { it.distanceMeters ?: Double.MAX_VALUE }
    .thenBy { it.place.name }

/** Uses the top of the price range. Both limits are exclusive, so equal tops fit. */
internal fun fitsMealBudget(place: Place, budget: BudgetBracket?): Boolean {
    val price = place.price ?: return true
    val limit = budget?.maxPrice ?: return true
    val top = price.max ?: return false
    return top <= limit
}

/** Bars need a block from 17:00, which includes the hours after midnight of the same Itinerary. */
private fun barsAllowed(search: MealSearch): Boolean {
    if (search.context.audience.hidesBars) return false
    val start = search.itinerary.resolve(search.block.start, search.context.zone)
    return start.toLocalDate().isAfter(search.itinerary.date) || !start.toLocalTime().isBefore(BarsOpenFrom)
}

private fun isOpenDuring(
    place: Place,
    interval: PlanningWindow,
    zone: ZoneId,
    isOpenAt: (Place, ZonedDateTime) -> Boolean,
): Boolean {
    val lastMinute = interval.end.minusSeconds(60)
    var check = interval.start
    while (check.isBefore(lastMinute)) {
        if (!isOpenAt(place, check.atZone(zone))) return false
        check = check.plusSeconds(OPEN_CHECK_STEP_SECONDS)
    }
    return isOpenAt(place, lastMinute.atZone(zone))
}

private fun matchesCuisine(place: Place, preferred: List<CuisineGroup>): Boolean =
    preferred.any { group ->
        group.venueType == place.venueType || place.cuisines.any { it in group.cuisines }
    }
