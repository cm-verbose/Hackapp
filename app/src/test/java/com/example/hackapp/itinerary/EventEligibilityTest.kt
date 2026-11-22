package com.example.hackapp.itinerary

import com.example.hackapp.data.Cost
import com.example.hackapp.data.CostStatus
import com.example.hackapp.data.EventStatus
import com.example.hackapp.data.TargetAudience
import com.example.hackapp.profile.AgeRange
import com.example.hackapp.profile.AudiencePreference
import com.example.hackapp.profile.BudgetBracket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventEligibilityTest {
    @Test
    fun `cancelled and sold out Events are hidden`() {
        assertEquals(
            IneligibleReason.Cancelled,
            ineligibleReason(event("a", status = EventStatus.CANCELLED), context()),
        )
        assertEquals(
            IneligibleReason.SoldOut,
            ineligibleReason(event("a", status = EventStatus.SOLD_OUT), context()),
        )
    }

    @Test
    fun `Events without a usable start are hidden`() {
        assertEquals(IneligibleReason.NoUsableStart, ineligibleReason(event("a", start = null), context()))
        assertEquals(IneligibleReason.NoUsableStart, ineligibleReason(event("a", start = "2026-11-28"), context()))
    }

    @Test
    fun `both offset formats in the bundled data are usable`() {
        assertNotNull(event("a", start = "2026-11-28T19:00:00-05:00").startTime())
        assertNotNull(event("a", start = "2026-11-28T19:00:00-0500").startTime())
        assertEquals(
            event("a", start = "2026-11-28T19:00:00-05:00").startTime()!!.toInstant(),
            event("a", start = "2026-11-28T19:00:00-0500").startTime()!!.toInstant(),
        )
    }

    @Test
    fun `an end that is not after the start is ignored`() {
        val start = at(Nov28, 19).toString()
        assertNull(event("a", start = start, end = start).publishedEnd())
    }

    @Test
    fun `past boundary uses the end, or the start when there is no end`() {
        val noEnd = event("a", start = at(Nov28, 19).toString())
        assertTrue(isEligible(noEnd, context(now = instant(Nov28, 19))))
        assertEquals(IneligibleReason.Past, ineligibleReason(noEnd, context(now = instant(Nov28, 19, 1))))

        val withEnd = event("b", start = at(Nov28, 19).toString(), end = at(Nov28, 21).toString())
        assertTrue(isEligible(withEnd, context(now = instant(Nov28, 21))))
        assertEquals(IneligibleReason.Past, ineligibleReason(withEnd, context(now = instant(Nov28, 21, 1))))
    }

    @Test
    fun `minimum age compares with the youngest age of the range`() {
        val over21 = event("a", audience = TargetAudience(minAge = 21))
        assertEquals(
            IneligibleReason.AboveAgeRange,
            ineligibleReason(over21, context(ageRange = AgeRange.From18To24)),
        )
        assertTrue(isEligible(over21, context(ageRange = AgeRange.From25To34)))
        assertTrue(isEligible(event("b", audience = TargetAudience(minAge = 18)), context(ageRange = AgeRange.From18To24)))
    }

    @Test
    fun `children-only Events need Family-friendly`() {
        val labels = listOf("Enfants", "Enfants d'âge préscolaire", "Enfants d’âge préscolaire", "Adolescents")
        val family = context().copy(audience = setOf(AudiencePreference.FamilyFriendly))
        labels.forEach { label ->
            val kids = event("a", audience = TargetAudience(description = label))
            assertEquals(label, IneligibleReason.ChildrenOnly, ineligibleReason(kids, context()))
            assertTrue(label, isEligible(kids, family))
        }
        val byAge = event("b", audience = TargetAudience(maxAge = 12))
        assertFalse(isEligible(byAge, context()))
        assertTrue(isEligible(byAge, family))
    }

    @Test
    fun `Family-friendly hides adults-only Events`() {
        val family = context().copy(audience = setOf(AudiencePreference.FamilyFriendly))
        assertEquals(
            IneligibleReason.AdultsOnly,
            ineligibleReason(event("a", audience = TargetAudience(minAge = 18)), family),
        )
        assertTrue(isEligible(event("b", audience = TargetAudience(minAge = 12)), family))
    }

    @Test
    fun `general audiences stay visible without Family-friendly`() {
        listOf("Pour tous", "Famille", "Adultes", "Personnes aînées").forEach { label ->
            assertTrue(label, isEligible(event("a", audience = TargetAudience(description = label)), context()))
        }
    }

    @Test
    fun `Events are never hidden by budget`() {
        val tight = context().copy(defaultEventBudget = BudgetBracket.Under20)
        val expensive = event("a", cost = Cost(CostStatus.PAID, minPrice = 400.0, maxPrice = 450.0))
        assertTrue(isEligible(expensive, tight))
        assertTrue(isEligible(event("b", cost = Cost(CostStatus.UNKNOWN)), tight))
    }

    @Test
    fun `Other Events remain eligible`() {
        assertTrue(isEligible(event("a", type = "other"), context()))
    }

    @Test
    fun `District count leaves out an Event already in progress`() {
        val running = event("a", start = at(Nov28, 18).toString(), end = at(Nov28, 21).toString())
        val window = night().planningWindow(Toronto)
        val during = context(now = instant(Nov28, 19))
        assertTrue(isEligible(running, during))
        assertEquals(emptyMap<String, Int>(), countEligibleByDistrict(listOf(running), window, during))
    }

    @Test
    fun `District count uses a half-open window and includes unknown prices`() {
        val itinerary = night()
        val window = itinerary.planningWindow(Toronto)
        val events = listOf(
            event("start", start = at(Nov28, 18).toString()),
            event("after-midnight", start = at(Nov28.plusDays(1), 1, 59).toString(), district = NEXT),
            event("at-end", start = at(Nov28.plusDays(1), 2).toString()),
            event("before", start = at(Nov28, 17, 59).toString()),
            event("cancelled", status = EventStatus.CANCELLED),
            event("no-district", district = null),
        )
        assertEquals(mapOf(HERE to 1, NEXT to 1), countEligibleByDistrict(events, window, context()))
    }
}
