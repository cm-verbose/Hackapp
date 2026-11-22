package com.example.hackapp.scaffold.screens.planning

import com.example.hackapp.citypack.EventCoverage
import com.example.hackapp.data.Cost
import com.example.hackapp.data.CostStatus
import com.example.hackapp.data.Event
import com.example.hackapp.data.Location
import com.example.hackapp.data.Schedule
import com.example.hackapp.ui.theme.StopType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PlanningWindowTest {
    private val coverage = EventCoverage(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 12, 6))

    private fun time(text: String) = LocalTime.parse(text)

    @Test
    fun defaultWindowsAreValid() {
        assertNull(validateTimeWindow(time("10:00"), time("18:00")))
        assertNull(validateTimeWindow(time("18:00"), time("02:00")))
    }

    @Test
    fun windowMustLastTwoHours() {
        assertNull(validateTimeWindow(time("23:00"), time("01:00")))
        assertEquals(TimeWindowError.TooShort, validateTimeWindow(time("23:30"), time("01:00")))
        assertEquals(TimeWindowError.TooShort, validateTimeWindow(time("12:00"), time("12:00")))
    }

    @Test
    fun endOutsideTheRangeIsRejected() {
        assertNull(validateTimeWindow(time("18:00"), time("04:00")))
        assertEquals(TimeWindowError.EndOutOfRange, validateTimeWindow(time("18:00"), time("04:01")))
        assertEquals(TimeWindowError.EndOutOfRange, validateTimeWindow(time("18:00"), time("17:00")))
        assertEquals(TimeWindowError.EndOutOfRange, validateTimeWindow(time("18:00"), time("09:00")))
        assertEquals(time("20:00"), earliestEnd(time("18:00")))
        assertEquals(time("01:30"), earliestEnd(time("23:30")))
    }

    @Test
    fun startAfterTwoLeavesNoValidEnd() {
        assertNull(validateTimeWindow(time("02:00"), time("04:00")))
        assertEquals(TimeWindowError.StartsTooLate, validateTimeWindow(time("02:01"), time("04:00")))
        assertEquals(TimeWindowError.StartsTooLate, validateTimeWindow(time("03:00"), time("05:00")))
        assertEquals(TimeWindowError.StartsTooLate, validateTimeWindow(time("03:59"), time("03:59")))
        assertEquals(TimeWindowError.TooShort, validateTimeWindow(time("04:00"), time("04:00")))
        assertEquals(LATEST_START, time("02:00"))
    }

    @Test
    fun continueWaitsForTheReplacementChoice() {
        val base = DistrictPickUiState(loading = false, selected = listOf("a", "b", "c"))
        assertTrue(base.canContinue)
        assertTrue(base.copy(previewId = "b").canContinue)
        assertFalse(base.copy(previewId = "d").canContinue)
        assertFalse(base.copy(previewId = "d", replaceTargetId = "b").canContinue)
        assertTrue(base.copy(selected = listOf("a", "b"), previewId = "d").canContinue)
    }

    @Test
    fun defaultDateIsNextDateWithData() {
        val data = setOf(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 24))
        assertEquals(LocalDate.of(2026, 11, 24), defaultPlanningDate(LocalDate.of(2026, 11, 22), coverage, data))
        assertEquals(LocalDate.of(2026, 11, 21), defaultPlanningDate(LocalDate.of(2026, 1, 1), coverage, data))
    }

    @Test
    fun defaultDateStaysInCoverageWithoutData() {
        assertEquals(coverage.lastDate, defaultPlanningDate(LocalDate.of(2027, 1, 1), coverage, emptySet()))
    }

    @Test
    fun addKeepsVisitOrderAndLimit() {
        assertEquals(listOf("a", "b"), addDistrict(listOf("a"), "b"))
        assertEquals(listOf("a"), addDistrict(listOf("a"), "a"))
        assertEquals(listOf("a", "b", "c"), addDistrict(listOf("a", "b", "c"), "d"))
    }

    @Test
    fun removeRenumbersRemainingDistricts() {
        assertEquals(listOf("a", "c"), removeDistrict(listOf("a", "b", "c"), "b"))
    }

    @Test
    fun replacementTakesTheVisitPosition() {
        assertEquals(listOf("a", "d", "c"), replaceDistrict(listOf("a", "b", "c"), "b", "d"))
        assertEquals(listOf("a", "b", "c"), replaceDistrict(listOf("a", "b", "c"), "x", "d"))
        assertEquals(listOf("a", "b", "c"), replaceDistrict(listOf("a", "b", "c"), "b", "c"))
    }

    @Test
    fun zeroEventAndLinkedDistrictsCannotBePicked() {
        assertTrue(isDistrictPickable(null, 2, selected = false))
        assertFalse(isDistrictPickable(null, 0, selected = false))
        assertFalse(isDistrictPickable(LINKED_CITY_GROUP, 5, selected = false))
        assertTrue(isDistrictPickable(null, 0, selected = true))
    }

    @Test
    fun summaryAppliesEligibilityAndCountsUnknownPrices() {
        val events = listOf(
            event("1", "a", "music", CostStatus.UNKNOWN),
            event("2", "a", "music", CostStatus.PAID),
            event("3", "a", "cinema", CostStatus.FREE),
            event("4", "a", "other", CostStatus.FREE),
            event("5", "b", "family", CostStatus.FREE),
            event("6", null, "music", CostStatus.FREE),
        )
        val summary = summarizeDistrictEvents(events) { it.id != "5" }
        assertEquals(4, summary.getValue("a").count)
        assertEquals(listOf(StopType.Music, StopType.Cinema), summary.getValue("a").types)
        assertFalse("b" in summary)
    }

    private fun event(id: String, district: String?, type: String, cost: CostStatus) = Event(
        id = id,
        title = id,
        type = type,
        location = Location(city = "Montréal"),
        schedule = Schedule("2026-11-28T18:00:00-05:00", timezone = "America/Toronto"),
        cost = Cost(cost),
        districtId = district,
    )
}
