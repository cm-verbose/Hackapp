package com.example.hackapp.scaffold.screens.onboarding

import com.example.hackapp.citypack.DistrictMapData
import com.example.hackapp.data.model.CuisineGroup
import com.example.hackapp.data.model.VenueType
import com.example.hackapp.profile.AgeRange
import com.example.hackapp.profile.AudiencePreference
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.Interest
import com.example.hackapp.profile.Profile
import com.example.hackapp.profile.VisitorType
import com.example.hackapp.profile.hidesBars
import com.example.hackapp.ui.theme.StopType
import kotlinx.serialization.Serializable
import java.text.Collator
import java.util.Locale

private const val LINKED_DISTRICT_GROUP = "linked"
private val EmailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** The answers given so far. Serializable so they survive process death. */
@Serializable
data class OnboardingDraft(
    val visitorType: VisitorType = VisitorType.Visitor,
    val name: String = "",
    val email: String = "",
    val ageRange: AgeRange? = null,
    val districtOfStayId: String? = null,
    val lunchBudget: BudgetBracket? = null,
    val dinnerBudget: BudgetBracket? = null,
    val eventBudget: BudgetBracket? = null,
    val lateNightEnabled: Boolean = false,
    val lateNightBudget: BudgetBracket? = null,
    val audience: Set<AudiencePreference> = emptySet(),
    val interests: Set<Interest> = emptySet(),
    val cuisineGroups: Set<String> = emptySet(),
) {
    val isEmailValid: Boolean get() = EmailPattern.matches(email.trim())

    val isProfileStepValid: Boolean
        get() = name.isNotBlank() && isEmailValid && ageRange != null && districtOfStayId != null

    val isBudgetStepValid: Boolean
        get() = lunchBudget != null && dinnerBudget != null && eventBudget != null &&
            (!lateNightEnabled || lateNightBudget != null)

    /** Returns null while a required answer is missing. */
    fun toProfile(): Profile? {
        if (!isProfileStepValid || !isBudgetStepValid) return null
        return Profile(
            visitorType = visitorType,
            name = name.trim(),
            email = email.trim(),
            ageRange = ageRange ?: return null,
            districtOfStayId = districtOfStayId ?: return null,
            lunchBudget = lunchBudget ?: return null,
            dinnerBudget = dinnerBudget ?: return null,
            eventBudget = eventBudget ?: return null,
            lateNightBudget = lateNightBudget.takeIf { lateNightEnabled },
            audience = audience,
            interests = interests,
            cuisineGroups = cuisineGroups,
        )
    }
}

data class DistrictOption(val id: String, val name: String)

/** The Districts a person can stay in, sorted for [locale]. Linked cities are excluded. */
fun selectableDistricts(map: DistrictMapData, locale: Locale): List<DistrictOption> {
    val collator = Collator.getInstance(locale)
    return map.districts
        .filter { it.groupId != LINKED_DISTRICT_GROUP }
        .map { DistrictOption(it.id, it.name) }
        .sortedWith(compareBy(collator) { it.name })
}

/** Cuisine chips for this audience: the bar group is hidden when the audience hides bars. */
fun visibleCuisineGroups(
    groups: List<CuisineGroup>,
    audience: Set<AudiencePreference>,
): List<CuisineGroup> =
    if (audience.hidesBars) groups.filterNot { it.venueType == VenueType.BAR } else groups

val Interest.stopType: StopType
    get() = when (this) {
        Interest.Music -> StopType.Music
        Interest.Festival -> StopType.Festival
        Interest.Theatre -> StopType.Theatre
        Interest.Comedy -> StopType.Comedy
        Interest.Exhibition -> StopType.Exhibition
        Interest.Sport -> StopType.Sport
        Interest.Cinema -> StopType.Cinema
        Interest.Family -> StopType.Family
    }
