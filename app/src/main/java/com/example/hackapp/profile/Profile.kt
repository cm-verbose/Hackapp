package com.example.hackapp.profile

import kotlinx.serialization.Serializable

/* Constant names are stored in saved Profiles: renaming one is a data migration. */

@Serializable
enum class VisitorType { Visitor, Resident }

@Serializable
enum class AgeRange { From18To24, From25To34, From35To44, From45To54, From55 }

/** Per-person budget. [maxPrice] is exclusive in CAD, and null means no upper limit. */
@Serializable
enum class BudgetBracket(val maxPrice: Int?) {
    Under20(20),
    Under50(50),
    Under100(100),
    From100(null),
}

@Serializable
enum class AudiencePreference { AdultsOnly, FamilyFriendly, NoAlcohol }

/** A type of Event a person prefers. Meals and bars are not Interests. */
@Serializable
enum class Interest { Music, Festival, Theatre, Comedy, Exhibition, Sport, Cinema, Family }

/** A person's lasting details and defaults, independent of any single Itinerary. */
@Serializable
data class Profile(
    val visitorType: VisitorType,
    val name: String,
    val email: String,
    val ageRange: AgeRange,
    val districtOfStayId: String,
    val lunchBudget: BudgetBracket,
    val dinnerBudget: BudgetBracket,
    val eventBudget: BudgetBracket,
    /** Null means the Late-night snack add-on is off. */
    val lateNightBudget: BudgetBracket? = null,
    val audience: Set<AudiencePreference> = emptySet(),
    val interests: Set<Interest> = emptySet(),
    /** Cuisine group codes from the Place data. */
    val cuisineGroups: Set<String> = emptySet(),
)

/** Bars are hidden for family-friendly and no-alcohol preferences. */
val Set<AudiencePreference>.hidesBars: Boolean
    get() = AudiencePreference.FamilyFriendly in this || AudiencePreference.NoAlcohol in this

/**
 * Applies a chip change from [previous] to [requested]. 18+ only and Family-friendly exclude each
 * other, so the one just selected replaces the other. No alcohol combines with either.
 */
fun normalizedAudience(
    previous: Set<AudiencePreference>,
    requested: Set<AudiencePreference>,
): Set<AudiencePreference> {
    val exclusive = setOf(AudiencePreference.AdultsOnly, AudiencePreference.FamilyFriendly)
    if (!requested.containsAll(exclusive)) return requested
    return requested - (previous intersect exclusive)
}
