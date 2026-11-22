package com.example.hackapp.domain

import com.example.hackapp.data.Cost
import com.example.hackapp.data.CostStatus
import com.example.hackapp.data.Event
import com.example.hackapp.data.EventStatus
import com.example.hackapp.data.Location
import com.example.hackapp.data.Schedule
import com.example.hackapp.data.TargetAudience
import com.example.hackapp.ui.theme.StopType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerRulesTest {
    private val zone = ZoneId.of("America/Toronto")

    private fun event(
        type: String = "music",
        status: EventStatus = EventStatus.SCHEDULED,
        audience: TargetAudience? = null,
        cost: Cost = Cost(CostStatus.FREE),
        start: String = "2026-11-21T20:00:00-05:00",
        end: String? = null,
    ) = Event(
        id = "e", title = "t", type = type, targetAudience = audience,
        location = Location(city = "Montréal", latitude = 45.5, longitude = -73.5),
        schedule = Schedule(start, end, "America/Toronto"), cost = cost,
        districtId = "ville-marie", status = status,
    )

    @Test fun typesMapToStopTypesAndOtherIsHidden() {
        assertEquals(StopType.Music, eventStopType("music"))
        assertEquals(StopType.Theatre, eventStopType("theatre"))
        assertEquals(StopType.Family, eventStopType("family"))
        assertNull(eventStopType("other"))
        assertFalse(event(type = "other").isEligible(UserProfile(), Audience.ADULTS_ONLY))
    }

    @Test fun cancelledAndSoldOutAreHidden() {
        val p = UserProfile()
        assertFalse(event(status = EventStatus.CANCELLED).isEligible(p, Audience.ADULTS_ONLY))
        assertFalse(event(status = EventStatus.SOLD_OUT).isEligible(p, Audience.ADULTS_ONLY))
        assertTrue(event().isEligible(p, Audience.ADULTS_ONLY))
    }

    @Test fun minimumAgeAboveTheAgeBandHidesTheEvent() {
        val young = UserProfile(ageRange = AgeRange.A18_24)
        assertFalse(event(audience = TargetAudience(minAge = 30)).isEligible(young, Audience.ADULTS_ONLY))
        assertFalse(event(audience = TargetAudience(minAge = 21)).isEligible(young, Audience.ADULTS_ONLY))
        assertTrue(event(audience = TargetAudience(minAge = 18)).isEligible(young, Audience.ADULTS_ONLY))
    }

    @Test fun childrenOnlyEventsNeedTheFamilyAudience() {
        val kids = event(type = "family", audience = TargetAudience(maxAge = 10))
        assertFalse(kids.isEligible(UserProfile(), Audience.ADULTS_ONLY))
        assertTrue(kids.isEligible(UserProfile(), Audience.FAMILY_FRIENDLY))
    }

    @Test fun interestsRankAndUnknownPriceRanksLowerUnderABudget() {
        val liked = UserProfile(interestTypes = setOf(StopType.Music))
        assertTrue(event(type = "music").rank(liked, null) > event(type = "sport").rank(liked, null))
        val unknown = event(cost = Cost(CostStatus.UNKNOWN))
        assertTrue(event().rank(UserProfile(), 50) > unknown.rank(UserProfile(), 50))
        assertEquals(event().rank(UserProfile(), null), unknown.rank(UserProfile(), null))
    }

    @Test fun startAndEndAreConvertedToCityTime() {
        val e = event(start = "2026-11-21T20:00:00-05:00", end = "2026-11-21T22:30:00-05:00")
        assertEquals(LocalDateTime.of(2026, 11, 21, 20, 0), e.startLocal(zone))
        assertEquals(LocalDateTime.of(2026, 11, 21, 22, 30), e.endLocal(zone))
    }

    @Test fun mealBlockIsAnHourPlusFifteenMinutesEachSide() {
        val b = MealBlock(com.example.hackapp.data.model.MealSlot.LUNCH, LocalDateTime.of(2026, 11, 21, 12, 0))
        assertEquals(LocalDateTime.of(2026, 11, 21, 11, 45), b.blockStart)
        assertEquals(LocalDateTime.of(2026, 11, 21, 13, 15), b.blockEnd)
    }

    @Test fun nightWindowEndsAfterMidnightOnTheNextDay() {
        val w = PlanWindow.default(LocalDate.of(2026, 11, 21), ItineraryKind.NIGHT_OUT)
        assertEquals(LocalDateTime.of(2026, 11, 21, 18, 0), w.startAt)
        assertEquals(LocalDateTime.of(2026, 11, 22, 2, 0), w.endAt)
    }

    @Test fun mealTimesOfferedMatchTheWindow() {
        val day = ItineraryPlanner.mealTimes(PlanWindow.default(LocalDate.of(2026, 11, 21), ItineraryKind.DAY_OUT))
        assertEquals(listOf(com.example.hackapp.data.model.MealSlot.LUNCH), day.map { it.slot })
        val night = ItineraryPlanner.mealTimes(PlanWindow.default(LocalDate.of(2026, 11, 21), ItineraryKind.NIGHT_OUT))
        assertTrue(night.any { it.slot == com.example.hackapp.data.model.MealSlot.DINNER })
    }

    @Test fun unpublishedEndNeedsAChosenDeparture() {
        val planner = ItineraryPlanner(FakeEvents, { error("unused") }, { _, _, _ -> error("unused") }, zone)
        assertNull(planner.eventStop(event(), null))
        val stop = planner.eventStop(event(), java.time.LocalTime.of(22, 0))!!
        assertEquals(LocalDateTime.of(2026, 11, 21, 22, 0), stop.departure)
    }
}

private object FakeEvents : com.example.hackapp.data.repository.EventRepository {
    override suspend fun search(query: com.example.hackapp.data.repository.EventQuery) = emptyList<Event>()
    override suspend fun getEvent(id: String): Event? = null
    override suspend fun countByDistrict(query: com.example.hackapp.data.repository.EventQuery) = emptyMap<String, Int>()
}
