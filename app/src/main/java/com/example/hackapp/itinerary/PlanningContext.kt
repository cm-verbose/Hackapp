package com.example.hackapp.itinerary

import com.example.hackapp.data.Event
import com.example.hackapp.profile.AgeRange
import com.example.hackapp.profile.AudiencePreference
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.Interest
import com.example.hackapp.profile.Profile
import java.text.Normalizer
import java.time.DateTimeException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The person and moment that Planning decisions are made for. */
data class PlanningContext(
    val now: Instant,
    val zone: ZoneId,
    val ageRange: AgeRange,
    val audience: Set<AudiencePreference> = emptySet(),
    val interests: Set<Interest> = emptySet(),
    /** Cuisine group codes from the Place data. */
    val cuisineGroups: Set<String> = emptySet(),
    val defaultEventBudget: BudgetBracket? = null,
    val defaultMealBudgets: Map<MealKind, BudgetBracket?> = emptyMap(),
) {
    companion object {
        fun from(profile: Profile, now: Instant, zone: ZoneId) = PlanningContext(
            now = now,
            zone = zone,
            ageRange = profile.ageRange,
            audience = profile.audience,
            interests = profile.interests,
            cuisineGroups = profile.cuisineGroups,
            defaultEventBudget = profile.eventBudget,
            defaultMealBudgets = mapOf(
                MealKind.Lunch to profile.lunchBudget,
                MealKind.Dinner to profile.dinnerBudget,
                MealKind.LateBite to profile.lateNightBudget,
            ),
        )
    }
}

/** A half-open span of time: [start] is inside it and [end] is not. */
data class PlanningWindow(val start: Instant, val end: Instant) {
    operator fun contains(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(end)

    fun overlaps(otherStart: Instant, otherEnd: Instant): Boolean =
        otherStart.isBefore(end) && start.isBefore(otherEnd)

    fun encloses(other: PlanningWindow): Boolean = !other.start.isBefore(start) && !other.end.isAfter(end)
}

fun Itinerary.planningWindow(zone: ZoneId) =
    PlanningWindow(windowStart(zone).toInstant(), windowEnd(zone).toInstant())

/** Stops that still count: everything except those marked Skipped, in visit order. */
val Itinerary.activeStops: List<Stop> get() = stops.filter { it.status != StopStatus.Skipped }

/** Meal Blocks that reserve time. Skipped blocks and declined Meal planning reserve nothing. */
val Itinerary.activeMealBlocks: List<MealBlock>
    get() = if (planMeals == false) emptyList() else mealBlocks.filterNot { it.skipped }

fun MealBlock.interval(itinerary: Itinerary, zone: ZoneId): PlanningWindow {
    val blockStart = itinerary.resolve(start, zone).toInstant()
    return PlanningWindow(blockStart, blockStart.plusSeconds(kind.blockMinutes * 60L))
}

/** The bundled data mixes `-05:00` and `-0500` offsets, and both are usable. Seconds are optional. */
private val EventTimestamp = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm[:ss][XXX][XX]")

private fun parseEventTime(text: String?): OffsetDateTime? {
    if (text.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(text.trim(), EventTimestamp)
    } catch (_: DateTimeException) {
        null
    }
}

/** Null when the Event has no usable start. */
fun Event.startTime(): OffsetDateTime? = parseEventTime(schedule.startDate)

/** Null when no end was published, or the published end is not after the start. */
fun Event.publishedEnd(): OffsetDateTime? {
    val start = startTime() ?: return null
    return parseEventTime(schedule.endDate)?.takeIf { it.isAfter(start) }
}

/**
 * Identifies all showtimes of one title at one venue on one day. It is also the ticket price
 * report key, so it uses the Event's own time zone and never the device's.
 */
@JvmInline
value class ShowtimeKey(val value: String)

fun Event.showtimeKey(): ShowtimeKey {
    val venue = location.venueName?.takeIf { it.isNotBlank() }
        ?: location.address?.takeIf { it.isNotBlank() }
        ?: location.latitude?.let { lat -> location.longitude?.let { lng -> "$lat,$lng" } }
        ?: id
    return ShowtimeKey("${normalizedLabel(title)}|${normalizedLabel(venue)}|${localDay()}")
}

private fun Event.localDay(): String {
    val start = startTime() ?: return schedule.startDate.take(10)
    val day = try {
        start.atZoneSameInstant(ZoneId.of(schedule.timezone)).toLocalDate()
    } catch (_: DateTimeException) {
        start.toLocalDate()
    }
    return day.toString()
}

private val CombiningMarks = Regex("\\p{M}+")
private val Whitespace = Regex("\\s+")

/** Lowercase, without accents, with single spaces and straight apostrophes. */
internal fun normalizedLabel(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(CombiningMarks, "")
        .replace('’', '\'')
        .lowercase(Locale.ROOT)
        .trim()
        .replace(Whitespace, " ")
