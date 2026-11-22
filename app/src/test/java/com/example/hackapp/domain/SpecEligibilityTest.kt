package com.example.hackapp.domain

import com.example.hackapp.data.TargetAudience
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T13a: Event eligibility rules taken word for word from prep/design/screen-list.md
 * ("Hide Events whose minimum age is above the person's age range, such as 21+ Events for someone
 * aged 18-24").
 */
class SpecEligibilityTest {
    /**
     * EXPECTED TO FAIL until the age-rule fix (P1-02, proposed T16). The current rule compares with
     * the band's maximum (24), so a 21+ Event is shown to an 18-24 person.
     * When it is fixed, flip the second assertion of PlannerRulesTest.minimumAgeAboveTheAgeBandHidesTheEvent.
     */
    @Test fun aTwentyOnePlusEventIsHiddenFor18To24() {
        val young = UserProfile(ageRange = AgeRange.A18_24)
        assertFalse(ev("bar21", "21:00", audience = TargetAudience(minAge = 21)).isEligible(young, Audience.ADULTS_ONLY))
    }

    @Test fun aTwentyOnePlusEventIsShownFrom25() {
        val p = UserProfile(ageRange = AgeRange.A25_34)
        assertTrue(ev("bar21", "21:00", audience = TargetAudience(minAge = 21)).isEligible(p, Audience.ADULTS_ONLY))
    }

    @Test fun anEighteenPlusEventIsShownToEveryAccountHolder() {
        for (band in AgeRange.entries) {
            assertTrue("$band", ev("e18", "21:00", audience = TargetAudience(minAge = 18)).isEligible(UserProfile(ageRange = band), Audience.ADULTS_ONLY))
        }
    }
}
