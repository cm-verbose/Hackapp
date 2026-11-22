package com.example.hackapp.scaffold.screens.planning

import com.example.hackapp.data.model.PriceRange
import com.example.hackapp.data.model.VenueType
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.itinerary.MealOption
import com.example.hackapp.itinerary.MealSearch
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.interval
import com.example.hackapp.itinerary.mealOptions
import com.example.hackapp.itinerary.mealStop
import com.example.hackapp.itinerary.previousStopBefore
import com.example.hackapp.itinerary.latLng
import com.example.hackapp.services.LegEstimator
import com.example.hackapp.services.LegMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.ZonedDateTime

/** **Here** and **In [next District]**. */
enum class MealTab { Here, NextDistrict }

/** The price of a Meal option. The range is shown as a range, but the cost counts its top. */
sealed interface MealPrice {
    /**
     * [countedCad] is the top of the range. It is null for "$100+", which is left out of the
     * running total and counted as an unknown price, as in the budget summary.
     */
    data class Range(val range: PriceRange, val countedCad: Int?) : MealPrice

    /** Stays visible, ranks lower, and shows as "+ unknown price". */
    data object Unknown : MealPrice
}

data class MealOptionRow(
    val placeId: Long,
    val name: String,
    val venueType: VenueType,
    /** Straight-line distance from the previous Stop, rounded. Null without a reference point. */
    val distanceMeters: Int?,
    /** Walking time in minutes, without buffer. Null without a reference point. */
    val walkingMinutes: Int?,
    val price: MealPrice,
    val inNextDistrict: Boolean,
    val matchesCuisine: Boolean,
)

data class MealPickerUiState(
    val blockId: String,
    val kind: MealKind,
    val blockStart: ZonedDateTime,
    val blockEnd: ZonedDateTime,
    /** One tab, or two when another District follows. */
    val tabs: List<MealTab>,
    val selectedTab: MealTab,
    /** Null when there is no next District, and the second tab is omitted. */
    val nextDistrictId: String?,
    val here: List<MealOptionRow>,
    val nextDistrict: List<MealOptionRow>,
) {
    val options: List<MealOptionRow> get() = if (selectedTab == MealTab.NextDistrict) nextDistrict else here

    /** The selected tab has nothing to show. */
    val isEmpty: Boolean get() = options.isEmpty()

    /** Both tabs are empty, so only Skip this Meal remains. */
    val hasNoOptions: Boolean get() = here.isEmpty() && nextDistrict.isEmpty()
}

sealed interface MealPickerOutcome {
    /**
     * Add [stop] with `Itinerary.withStop`. When [moveToDistrictId] is set, planning moves to that
     * District after the Meal.
     */
    data class Chosen(val stop: Stop, val moveToDistrictId: String?) : MealPickerOutcome

    /** Apply `Itinerary.withMealSkipped(blockId)`, which frees the block. */
    data class Skipped(val blockId: String) : MealPickerOutcome
}

/** A skipped block no longer reserves time or hides Events. */
fun Itinerary.withMealSkipped(blockId: String): Itinerary =
    copy(mealBlocks = mealBlocks.map { if (it.id == blockId) it.copy(skipped = true) else it })

fun mealPickerRows(search: MealSearch, estimator: LegEstimator): Pair<List<MealOptionRow>, List<MealOptionRow>> {
    val zone = search.context.zone
    val previous = previousStopBefore(search.itinerary, search.block, zone)
    val origin = previous?.latLng ?: search.origin
    val options = mealOptions(search, estimator)

    fun row(option: MealOption): MealOptionRow {
        val place = option.place
        val price = place.price
        return MealOptionRow(
            placeId = place.id,
            name = place.name,
            venueType = place.venueType,
            distanceMeters = option.distanceMeters?.let { Math.round(it).toInt() },
            walkingMinutes = origin?.let { estimator.estimate(it, place.location, LegMode.WALK).travelMinutes },
            price = if (price == null) MealPrice.Unknown else MealPrice.Range(price, option.topPriceCad),
            inNextDistrict = option.inNextDistrict,
            matchesCuisine = option.matchesCuisine,
        )
    }
    return options.here.map(::row) to options.nextDistrict.map(::row)
}

/** State holder for the Meal picker sheet. It has no Android dependencies. */
class MealPickerHolder(
    private val search: MealSearch,
    estimator: LegEstimator,
) {
    private val zone = search.context.zone
    private val nextDistrictId = search.nextDistrictId?.takeIf { it != search.currentDistrictId }

    private val _state: MutableStateFlow<MealPickerUiState>
    val state: StateFlow<MealPickerUiState> get() = _state.asStateFlow()

    init {
        val (here, next) = mealPickerRows(search, estimator)
        val interval = search.block.interval(search.itinerary, zone)
        _state = MutableStateFlow(
            MealPickerUiState(
                blockId = search.block.id,
                kind = search.block.kind,
                blockStart = interval.start.atZone(zone),
                blockEnd = interval.end.atZone(zone),
                tabs = if (nextDistrictId != null) listOf(MealTab.Here, MealTab.NextDistrict) else listOf(MealTab.Here),
                selectedTab = MealTab.Here,
                nextDistrictId = nextDistrictId,
                here = here,
                nextDistrict = next,
            ),
        )
    }

    /** Ignored when the tab is not offered. */
    fun selectTab(tab: MealTab) {
        _state.update { if (tab in it.tabs) it.copy(selectedTab = tab) else it }
    }

    /** Null when the option is not on the list, or the Place has no District. */
    fun choose(placeId: Long): MealPickerOutcome.Chosen? {
        val row = (_state.value.here + _state.value.nextDistrict).firstOrNull { it.placeId == placeId } ?: return null
        val place = search.places.firstOrNull { it.id == placeId } ?: return null
        val stop = mealStop(place, search.block, search.itinerary, zone) ?: return null
        return MealPickerOutcome.Chosen(stop, moveToDistrictId = if (row.inNextDistrict) nextDistrictId else null)
    }

    fun skip(): MealPickerOutcome.Skipped = MealPickerOutcome.Skipped(search.block.id)
}
