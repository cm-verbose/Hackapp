package com.example.hackapp.scaffold.screens.onboarding

import com.example.hackapp.citypack.DistrictMapData
import com.example.hackapp.citypack.DistrictShape
import com.example.hackapp.data.model.CuisineGroup
import com.example.hackapp.data.model.LocalizedText
import com.example.hackapp.data.model.VenueType
import com.example.hackapp.profile.AgeRange
import com.example.hackapp.profile.AudiencePreference.AdultsOnly
import com.example.hackapp.profile.AudiencePreference.FamilyFriendly
import com.example.hackapp.profile.AudiencePreference.NoAlcohol
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.Interest
import com.example.hackapp.profile.normalizedAudience
import com.example.hackapp.ui.theme.StopType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class OnboardingDraftTest {
    private val complete = OnboardingDraft(
        name = " Camille ",
        email = " camille@exemple.ca ",
        ageRange = AgeRange.From18To24,
        districtOfStayId = "district-a",
        lunchBudget = BudgetBracket.Under20,
        dinnerBudget = BudgetBracket.Under50,
        eventBudget = BudgetBracket.Under100,
    )

    @Test
    fun `profile step requires name, valid email, age and district`() {
        assertTrue(complete.isProfileStepValid)
        assertFalse(complete.copy(name = "  ").isProfileStepValid)
        assertFalse(complete.copy(email = "camille@exemple").isProfileStepValid)
        assertFalse(complete.copy(email = "camille exemple.ca").isProfileStepValid)
        assertFalse(complete.copy(ageRange = null).isProfileStepValid)
        assertFalse(complete.copy(districtOfStayId = null).isProfileStepValid)
    }

    @Test
    fun `budget step requires three budgets`() {
        assertTrue(complete.isBudgetStepValid)
        assertFalse(complete.copy(lunchBudget = null).isBudgetStepValid)
        assertFalse(complete.copy(dinnerBudget = null).isBudgetStepValid)
        assertFalse(complete.copy(eventBudget = null).isBudgetStepValid)
    }

    @Test
    fun `late night needs a bracket only when on`() {
        assertFalse(complete.copy(lateNightEnabled = true).isBudgetStepValid)
        assertTrue(complete.copy(lateNightEnabled = true, lateNightBudget = BudgetBracket.Under20).isBudgetStepValid)
    }

    @Test
    fun `late night bracket is dropped when turned off`() {
        val draft = complete.copy(lateNightEnabled = false, lateNightBudget = BudgetBracket.Under50)
        assertNull(draft.toProfile()?.lateNightBudget)
    }

    @Test
    fun `profile is trimmed and incomplete draft gives none`() {
        val profile = complete.toProfile()!!
        assertEquals("Camille", profile.name)
        assertEquals("camille@exemple.ca", profile.email)
        assertNull(complete.copy(ageRange = null).toProfile())
    }

    @Test
    fun `adults only and family friendly replace each other`() {
        assertEquals(setOf(FamilyFriendly), normalizedAudience(setOf(AdultsOnly), setOf(AdultsOnly, FamilyFriendly)))
        assertEquals(setOf(AdultsOnly), normalizedAudience(setOf(FamilyFriendly), setOf(AdultsOnly, FamilyFriendly)))
    }

    @Test
    fun `no alcohol combines with either audience`() {
        assertEquals(setOf(AdultsOnly, NoAlcohol), normalizedAudience(setOf(AdultsOnly), setOf(AdultsOnly, NoAlcohol)))
        assertEquals(
            setOf(FamilyFriendly, NoAlcohol),
            normalizedAudience(setOf(AdultsOnly, NoAlcohol), setOf(AdultsOnly, FamilyFriendly, NoAlcohol)),
        )
    }

    @Test
    fun `audience can be empty`() {
        assertEquals(emptySet<Any>(), normalizedAudience(setOf(NoAlcohol), emptySet()))
    }

    @Test
    fun `interests are event stop types only`() {
        assertEquals(
            StopType.entries.toSet() - StopType.Meal - StopType.Bar,
            Interest.entries.map { it.stopType }.toSet(),
        )
    }

    @Test
    fun `linked districts are excluded and names sorted with accents`() {
        val map = DistrictMapData(
            cityId = "test",
            viewBox = listOf(0f, 0f, 1f, 1f),
            districts = listOf(
                DistrictShape("z", "Zeta", null, listOf(0f, 0f), ""),
                DistrictShape("e", "École", null, listOf(0f, 0f), ""),
                DistrictShape("l", "Linked", "linked", listOf(0f, 0f), ""),
            ),
        )
        assertEquals(listOf("e", "z"), selectableDistricts(map, Locale.CANADA_FRENCH).map { it.id })
    }

    @Test
    fun `bar cuisine group hides with the audience`() {
        val food = CuisineGroup("food", "", LocalizedText("a", "a"), emptySet(), null)
        val bars = CuisineGroup("bars", "", LocalizedText("b", "b"), emptySet(), VenueType.BAR)
        assertEquals(listOf(food, bars), visibleCuisineGroups(listOf(food, bars), setOf(AdultsOnly)))
        assertEquals(listOf(food), visibleCuisineGroups(listOf(food, bars), setOf(NoAlcohol)))
        assertEquals(listOf(food), visibleCuisineGroups(listOf(food, bars), setOf(FamilyFriendly)))
    }
}
