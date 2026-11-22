package com.example.hackapp.scaffold.screens.planning

import com.example.hackapp.citypack.EventCoverage
import com.example.hackapp.data.Event
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.OutingKind
import com.example.hackapp.ui.theme.StopType
import java.time.LocalDate
import java.time.LocalTime

private const val MINUTES_PER_DAY = 24 * 60
private const val MIN_WINDOW_MINUTES = 120

/** An Itinerary's day runs from 04:00 to 04:00, so no window can continue past 04:00. */
private const val DAY_BOUNDARY_MINUTES = 4 * 60

const val MAX_DISTRICTS = 3

/** The group id of independent linked cities in districts.json. */
const val LINKED_CITY_GROUP = "linked"

enum class TimeWindowError { TooShort, EndsTooLate }

fun OutingKind.defaultStart(): LocalTime = when (this) {
    OutingKind.Day -> LocalTime.of(10, 0)
    OutingKind.Night -> LocalTime.of(18, 0)
}

fun OutingKind.defaultEnd(): LocalTime = when (this) {
    OutingKind.Day -> LocalTime.of(18, 0)
    OutingKind.Night -> LocalTime.of(2, 0)
}

/** Checks a window of at least 2 hours that ends no later than 04:00. */
fun validateTimeWindow(start: LocalTime, end: LocalTime): TimeWindowError? {
    if (start == end) return TimeWindowError.TooShort
    val startMinutes = minutesAfterBoundary(start)
    val endMinutes = minutesAfterBoundary(end).let { if (it == 0) MINUTES_PER_DAY else it }
    return when {
        endMinutes <= startMinutes -> TimeWindowError.EndsTooLate
        endMinutes - startMinutes < MIN_WINDOW_MINUTES -> TimeWindowError.TooShort
        else -> null
    }
}

private fun minutesAfterBoundary(time: LocalTime): Int =
    Math.floorMod(time.hour * 60 + time.minute - DAY_BOUNDARY_MINUTES, MINUTES_PER_DAY)

/** The first date from today on that has data, or the nearest covered date if none does. */
fun defaultPlanningDate(today: LocalDate, coverage: EventCoverage, datesWithData: Set<LocalDate>): LocalDate {
    val from = maxOf(today, coverage.firstDate)
    return datesWithData.filter { it >= from && it <= coverage.lastDate }.minOrNull()
        ?: minOf(from, coverage.lastDate)
}

/* District selection, in visit order. Every change returns a new list. */

fun addDistrict(selected: List<String>, id: String): List<String> =
    if (id in selected || selected.size >= MAX_DISTRICTS) selected else selected + id

/** Removes [id]. Later Districts move up, which renumbers them. */
fun removeDistrict(selected: List<String>, id: String): List<String> = selected - id

/** Puts [newId] at the visit position of [replacedId]. */
fun replaceDistrict(selected: List<String>, replacedId: String, newId: String): List<String> =
    if (replacedId !in selected || newId in selected) selected
    else selected.map { if (it == replacedId) newId else it }

/** What the map needs to know about one District's Events in the Itinerary's window. */
data class DistrictEvents(val count: Int, val types: List<StopType>)

/**
 * Counts the Events that pass [eligibility] per District, with their types by frequency.
 * Unknown prices count and budget never filters. Types with no clear label are left out.
 */
fun summarizeDistrictEvents(
    events: List<Event>,
    eligibility: (Event) -> Boolean,
): Map<String, DistrictEvents> = events
    .filter { it.districtId != null && eligibility(it) }
    .groupBy { it.districtId!! }
    .mapValues { (_, inDistrict) ->
        val types = inDistrict
            .mapNotNull { event -> eventStopTypes.firstOrNull { it.name.equals(event.type, ignoreCase = true) } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<StopType, Int>> { it.value }.thenBy { it.key.ordinal })
            .map { it.key }
        DistrictEvents(inDistrict.size, types)
    }

private val eventStopTypes = StopType.entries - StopType.Meal - StopType.Bar

/** Linked cities and Districts without Events cannot be picked. A selected one stays removable. */
fun isDistrictPickable(groupId: String?, count: Int, selected: Boolean): Boolean =
    selected || (groupId != LINKED_CITY_GROUP && count > 0)

/**
 * The single place where a change to the date, time or Districts meets saved Stops and Meal Blocks.
 * [updated] is about to be saved; the returned Itinerary is what gets saved instead. District pick
 * autosaves its selection, so this runs on every Continue with saved Stops and must be idempotent.
 */
fun interface PlanningChangeReconciler {
    suspend fun reconcile(previous: Itinerary, updated: Itinerary): Itinerary
}

/** Placeholder until the Planning engine supplies the real rule: keeps every saved Stop. */
val KeepSavedStops = PlanningChangeReconciler { _, updated -> updated }
