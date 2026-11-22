package com.example.hackapp.itinerary

import com.example.hackapp.data.Event
import com.example.hackapp.data.EventStatus
import com.example.hackapp.profile.AgeRange
import com.example.hackapp.profile.AudiencePreference
import com.example.hackapp.profile.ProfileRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Why an Event is hidden. Price is deliberately absent: Events are never hidden by budget. */
enum class IneligibleReason { Cancelled, SoldOut, NoUsableStart, Past, AboveAgeRange, AdultsOnly, ChildrenOnly }

private const val ADULT_AGE = 18

/** Audience labels in the source data that describe minors only. */
private val MinorsOnlyAudiences = setOf("enfants", "enfants d'age prescolaire", "adolescents")

/** The youngest age in the range, so a 21+ Event is hidden for someone aged 18-24. */
internal val AgeRange.youngestAge: Int
    get() = when (this) {
        AgeRange.From18To24 -> 18
        AgeRange.From25To34 -> 25
        AgeRange.From35To44 -> 35
        AgeRange.From45To54 -> 45
        AgeRange.From55 -> 55
    }

fun Event.isChildrenOnly(): Boolean {
    val audience = targetAudience ?: return false
    if (audience.maxAge != null && audience.maxAge < ADULT_AGE) return true
    return audience.description?.let { normalizedLabel(it) in MinorsOnlyAudiences } == true
}

/** The first rule that hides this Event, or null when it may be shown. */
fun ineligibleReason(event: Event, context: PlanningContext): IneligibleReason? {
    when (event.status) {
        EventStatus.CANCELLED -> return IneligibleReason.Cancelled
        EventStatus.SOLD_OUT -> return IneligibleReason.SoldOut
        EventStatus.SCHEDULED -> Unit
    }
    val start = event.startTime() ?: return IneligibleReason.NoUsableStart
    if ((event.publishedEnd() ?: start).toInstant().isBefore(context.now)) return IneligibleReason.Past
    val minAge = event.targetAudience?.minAge
    if (minAge != null && minAge > context.ageRange.youngestAge) return IneligibleReason.AboveAgeRange
    if (minAge != null && minAge >= ADULT_AGE && AudiencePreference.FamilyFriendly in context.audience) {
        return IneligibleReason.AdultsOnly
    }
    if (event.isChildrenOnly() && AudiencePreference.FamilyFriendly !in context.audience) {
        return IneligibleReason.ChildrenOnly
    }
    return null
}

/** Events are never hidden by budget, and "other" Events remain eligible in their own group. */
fun isEligible(event: Event, context: PlanningContext): Boolean = ineligibleReason(event, context) == null

/** This person's Event filter. Without a saved Profile, no audience rule can apply. */
suspend fun eventEligibility(
    profiles: ProfileRepository,
    uid: String?,
    zone: ZoneId,
    now: Instant = Instant.now(),
): (Event) -> Boolean {
    val profile = uid?.let { profiles.getProfile(it) } ?: return { true }
    val context = PlanningContext.from(profile, now, zone)
    return { event -> isEligible(event, context) }
}

/**
 * Predicate for District counts: fits the audience and starts in [window], at any price. A fixed
 * Event already in progress is not counted, but a drop-in still running with time left is.
 */
fun districtCountPredicate(window: PlanningWindow, context: PlanningContext): (Event) -> Boolean = { event ->
    isEligible(event, context) &&
        (event.startTime()?.toInstant()?.let { it in window && !it.isBefore(context.now) } == true ||
            event.runsIn(window, context.now))
}

/** A drop-in already running that still leaves a meaningful stay inside [window]. */
private fun Event.runsIn(window: PlanningWindow, now: java.time.Instant): Boolean {
    if (!isDropIn()) return false
    val start = startTime()?.toInstant() ?: return false
    val end = publishedEnd()?.toInstant() ?: return false
    val from = maxOf(start, window.start, now)
    val until = minOf(end, window.end)
    return from.isBefore(window.end) && Duration.between(from, until).toMinutes() >= MIN_DROP_IN_STAY_MINUTES
}

/** Number of matching Events per District id. Districts with no match are absent. */
fun countEligibleByDistrict(
    events: Iterable<Event>,
    window: PlanningWindow,
    context: PlanningContext,
): Map<String, Int> {
    val counts = districtCountPredicate(window, context)
    return events.asSequence().filter(counts).mapNotNull { it.districtId }.groupingBy { it }.eachCount()
}
