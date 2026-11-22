package com.example.hackapp.scaffold.screens.itinerary

import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.OutingKind
import com.example.hackapp.itinerary.PlanningStep
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.itinerary.StopStatus
import com.example.hackapp.language.AppLanguage
import com.example.hackapp.scaffold.navigation.ArchivedItineraryRoute
import com.example.hackapp.scaffold.navigation.ItineraryReviewRoute
import com.example.hackapp.scaffold.navigation.PastItineraryRoute
import com.example.hackapp.scaffold.navigation.PlanningDateTimeRoute
import com.example.hackapp.scaffold.navigation.PlanningDistrictPickRoute
import com.example.hackapp.scaffold.navigation.PlanningMealChoiceRoute
import com.example.hackapp.scaffold.navigation.PlanningStopSelectionRoute
import com.example.hackapp.scaffold.navigation.UnderwayLegRoute
import com.example.hackapp.scaffold.screens.home.firstNameOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.Locale

class ItineraryPresentationTest {
    private fun itinerary(
        id: String,
        phase: ItineraryPhase,
        step: PlanningStep = PlanningStep.DateTime,
        date: LocalDate = LocalDate.of(2026, 11, 28),
        stops: List<Stop> = emptyList(),
    ) = Itinerary(
        id = id,
        ownerUid = "uid",
        cityId = "mtl",
        phase = phase,
        step = step,
        date = date,
        outingKind = OutingKind.Night,
        startTime = LocalTime.of(18, 0),
        endTime = LocalTime.of(2, 0),
        stops = stops,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )

    private fun stop(id: String, sourceId: String, status: StopStatus, kind: StopKind = StopKind.Event): Stop {
        val start = OffsetDateTime.parse("2026-11-28T18:30:00-05:00")
        return Stop(id, kind, sourceId, id, "ville-marie", 45.5, -73.5, start, start.plusHours(1), status = status)
    }

    @Test
    fun pastCardsCountOnlyConfirmedStops() {
        val stops = listOf(
            stop("a", "s1", StopStatus.Confirmed),
            stop("b", "s2", StopStatus.Skipped),
            stop("c", "s3", StopStatus.Planned),
        )
        assertEquals(1, itinerary("p", ItineraryPhase.Completed, stops = stops).toCard(emptyMap()).stopCount)
        assertEquals(3, itinerary("d", ItineraryPhase.Planning, stops = stops).toCard(emptyMap()).stopCount)
    }

    @Test
    fun sectionsGroupByPhaseWithNewestPastFirst() {
        val sections = listOf(
            itinerary("old", ItineraryPhase.Completed, date = LocalDate.of(2026, 11, 21)),
            itinerary("draft", ItineraryPhase.Planning),
            itinerary("new", ItineraryPhase.Completed, date = LocalDate.of(2026, 11, 28)),
            itinerary("live", ItineraryPhase.Underway),
            itinerary("gone", ItineraryPhase.Archived),
        ).toSections()

        assertEquals("live", sections.underway?.id)
        assertEquals(listOf("draft"), sections.planning.map { it.id })
        assertEquals(listOf("new", "old"), sections.past.map { it.id })
        assertEquals(listOf("gone"), sections.archived.map { it.id })
    }

    @Test
    fun draftsReopenOnTheirSavedStep() {
        fun route(step: PlanningStep) = itinerary("a", ItineraryPhase.Planning, step).openRoute()

        assertEquals(PlanningDateTimeRoute("a"), route(PlanningStep.DateTime))
        assertEquals(PlanningDistrictPickRoute("a"), route(PlanningStep.Budget))
        assertEquals(PlanningDistrictPickRoute("a"), route(PlanningStep.Districts))
        assertEquals(PlanningMealChoiceRoute("a"), route(PlanningStep.Meals))
        assertEquals(PlanningStopSelectionRoute("a"), route(PlanningStep.Stops))
        assertEquals(ItineraryReviewRoute("a"), route(PlanningStep.Review))
    }

    @Test
    fun otherPhasesOpenTheirOwnPage() {
        assertEquals(UnderwayLegRoute, itinerary("a", ItineraryPhase.Underway).openRoute())
        assertEquals(PastItineraryRoute("a"), itinerary("a", ItineraryPhase.Completed).openRoute())
        assertEquals(ArchivedItineraryRoute("a"), itinerary("a", ItineraryPhase.Archived).openRoute())
    }

    @Test
    fun pastAndUnderwayItinerariesCannotBeDeleted() {
        assertTrue(itinerary("a", ItineraryPhase.Planning).canDelete)
        assertTrue(itinerary("a", ItineraryPhase.Archived).canDelete)
        assertFalse(itinerary("a", ItineraryPhase.Completed).canDelete)
        assertFalse(itinerary("a", ItineraryPhase.Underway).canDelete)
    }

    @Test
    fun stampsCountConfirmedStopsOncePerPlace() {
        val past = itinerary(
            "a",
            ItineraryPhase.Completed,
            stops = listOf(
                stop("1", "event-1", StopStatus.Confirmed),
                stop("2", "event-1", StopStatus.Confirmed),
                stop("3", "event-2", StopStatus.Skipped),
                stop("4", "event-1", StopStatus.Confirmed, StopKind.Meal),
            ),
        )

        assertEquals(listOf("1", "4"), past.earnedStampStops().map { it.id })
    }

    @Test
    fun greetingUsesTheFirstWordOfTheName() {
        assertEquals("Nikola", firstNameOf("  Nikola Tesla "))
        assertNull(firstNameOf("   "))
        assertNull(firstNameOf(null))
    }

    @Test
    fun anyNonEnglishLocaleResolvesToFrench() {
        assertEquals(AppLanguage.English, AppLanguage.resolvedFor(Locale.CANADA))
        assertEquals(AppLanguage.French, AppLanguage.resolvedFor(Locale.CANADA_FRENCH))
        assertEquals(AppLanguage.French, AppLanguage.resolvedFor(Locale.GERMAN))
    }
}
