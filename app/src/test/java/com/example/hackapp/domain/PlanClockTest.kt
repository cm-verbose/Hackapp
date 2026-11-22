package com.example.hackapp.domain

import com.example.hackapp.data.model.MealSlot
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** T7b + T18a: default date, past dates, and nothing that already started is offered. */
class PlanClockTest {
    private fun clockAt(hhmm: String): Clock = Clock.fixed(at(hhmm).atZone(ZONE).toInstant(), ZONE)

    @Test fun defaultDateIsTodayInsideTheDataWindow() {
        assertEquals(LocalDate.of(2026, 11, 22), FlowViewModel.defaultDate(LocalDate.of(2026, 11, 22)))
        assertEquals(FlowViewModel.FIRST_DATE, FlowViewModel.defaultDate(LocalDate.of(2026, 11, 1)))
        assertEquals(FlowViewModel.LAST_DATE, FlowViewModel.defaultDate(LocalDate.of(2026, 12, 25)))
    }

    @Test fun thePickerStartsAtTodaySoPastDatesCannotBeChosen() {
        // The picker's firstDate is defaultDate(today): yesterday falls before it.
        val first = FlowViewModel.defaultDate(LocalDate.of(2026, 11, 22))
        assertEquals(true, LocalDate.of(2026, 11, 21).isBefore(first))
        assertEquals(LocalDate.of(2026, 11, 22), first)
    }

    @Test fun eventsThatAlreadyStartedAreNotOffered() = runBlocking {
        val p = planner(listOf(ev("started", "13:00"), ev("now", "14:00"), ev("later", "15:00")), clock = clockAt("14:00"))
        val view = p.selection(input(), DISTRICT, previous = null, meal = null, mealChosen = false, chosenIds = emptySet())
        assertEquals(listOf("now", "later"), view.blocks.flatMap { b -> b.groups.flatMap { it.second } }.map { it.id })
        assertEquals(mapOf(DISTRICT to 2), p.countsByDistrict(input(), listOf(DISTRICT)))
    }

    @Test fun mealTimesThatAlreadyStartedAreDropped() {
        val window = PlanWindow(DAY, LocalTime.of(10, 0), LocalTime.of(23, 30))
        assertEquals(listOf(MealSlot.LUNCH, MealSlot.DINNER, MealSlot.EVENING), ItineraryPlanner.mealTimes(window).map { it.slot })
        assertEquals(listOf(MealSlot.DINNER, MealSlot.EVENING), ItineraryPlanner.mealTimes(window, at("13:00")).map { it.slot })
        assertEquals(listOf(MealSlot.DINNER, MealSlot.EVENING), ItineraryPlanner.mealTimes(window, at("12:00").plusMinutes(1)).map { it.slot })
        assertEquals(emptyList<MealSlot>(), ItineraryPlanner.mealTimes(window, at("22:30")).map { it.slot })
    }
}
