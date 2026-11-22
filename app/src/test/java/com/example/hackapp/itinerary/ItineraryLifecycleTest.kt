package com.example.hackapp.itinerary

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ItineraryLifecycleTest {
    private val legs = fixedLegs(20)
    private val nextDay = Nov28.plusDays(1)
    private val eventStop = stop("e", at(Nov28, 19), at(Nov28, 20))

    @Test
    fun `archive boundary is the exact window end, after midnight`() = runBlocking {
        val repository = InMemoryItineraryRepository()
        repository.saveItinerary(night())

        assertTrue(archiveExpiredDrafts(repository, UID, instant(nextDay, 1, 59), Toronto).isEmpty())
        assertEquals(ItineraryPhase.Planning, repository.getItinerary(UID, "it-1")!!.phase)

        assertEquals(1, archiveExpiredDrafts(repository, UID, instant(nextDay, 2), Toronto).size)
        assertEquals(ItineraryPhase.Archived, repository.getItinerary(UID, "it-1")!!.phase)
    }

    @Test
    fun `archive leaves other phases and other accounts alone`() = runBlocking {
        val repository = InMemoryItineraryRepository()
        repository.saveItinerary(night(id = "u", phase = ItineraryPhase.Underway))
        repository.saveItinerary(night(id = "c", phase = ItineraryPhase.Completed))
        repository.saveItinerary(night(id = "x").copy(ownerUid = "other"))
        val archived = archiveExpiredDrafts(repository, UID, instant(Nov28.plusDays(5), 12), Toronto)
        assertTrue(archived.isEmpty())
        assertEquals(ItineraryPhase.Planning, repository.getItinerary("other", "x")!!.phase)
    }

    @Test
    fun `archiving twice changes nothing more`() = runBlocking {
        val repository = InMemoryItineraryRepository()
        repository.saveItinerary(night())
        archiveExpiredDrafts(repository, UID, instant(nextDay, 3), Toronto)
        assertTrue(archiveExpiredDrafts(repository, UID, instant(nextDay, 4), Toronto).isEmpty())
    }

    @Test
    fun `restore waits for confirmation and returns to Planning`() = runBlocking {
        val repository = InMemoryItineraryRepository()
        val archived = night(stops = listOf(eventStop), phase = ItineraryPhase.Archived)
            .copy(step = PlanningStep.Review)
        repository.saveItinerary(archived)
        val newDate = LocalDate.of(2026, 12, 5)

        val preview = previewRestore(archived, newDate, legs, Toronto)
        assertEquals(ItineraryPhase.Archived, repository.getItinerary(UID, "it-1")!!.phase)
        assertEquals(StopRemovalReason.DateChanged, preview.removed.single().reason)

        val restored = restoreArchivedDraft(repository, preview, instant(Nov28, 12), Toronto)!!
        assertEquals(ItineraryPhase.Planning, restored.phase)
        assertEquals(newDate, restored.date)
        assertEquals(PlanningStep.Stops, restored.step)
        assertEquals(restored, repository.getItinerary(UID, "it-1"))
    }

    @Test
    fun `restore refuses an ended date and a non-Archived draft`() = runBlocking {
        val repository = InMemoryItineraryRepository()
        val archived = night(phase = ItineraryPhase.Archived)
        repository.saveItinerary(archived)
        val ended = previewRestore(archived, Nov28, legs, Toronto)
        assertNull(restoreArchivedDraft(repository, ended, instant(nextDay, 2), Toronto))

        val planning = previewRestore(night(), LocalDate.of(2026, 12, 5), legs, Toronto)
        assertNull(restoreArchivedDraft(repository, planning, instant(Nov28, 12), Toronto))
        assertEquals(ItineraryPhase.Archived, repository.getItinerary(UID, "it-1")!!.phase)
    }

    @Test
    fun `Start works on its own date, including after midnight`() {
        val itinerary = night(stops = listOf(eventStop))
        assertEquals(StartBlock.WrongDate, startBlock(itinerary, instant(Nov28.minusDays(1), 23, 59), Toronto))
        assertNull(startBlock(itinerary, instant(Nov28, 0), Toronto))
        assertNull(startBlock(itinerary, instant(nextDay, 1, 59), Toronto))
        assertEquals(StartBlock.WindowEnded, startBlock(itinerary, instant(nextDay, 2), Toronto))
    }

    @Test
    fun `a day out cannot start after its window on the same date`() {
        val itinerary = day(stops = listOf(stop("e", at(Nov28, 11), at(Nov28, 12))))
        assertNull(startBlock(itinerary, instant(Nov28, 17, 59), Toronto))
        assertEquals(StartBlock.WindowEnded, startBlock(itinerary, instant(Nov28, 18), Toronto))
    }

    @Test
    fun `demo mode skips only the date rule`() {
        val itinerary = night(stops = listOf(eventStop))
        assertNull(startBlock(itinerary, instant(Nov28.minusDays(3), 9), Toronto, demoMode = true))
        assertNull(startBlock(itinerary, instant(Nov28.plusDays(3), 9), Toronto, demoMode = true))
        assertEquals(StartBlock.NoEvents, startBlock(night(), instant(Nov28, 9), Toronto, demoMode = true))
        assertEquals(
            StartBlock.NotInPlanning,
            startBlock(itinerary.copy(phase = ItineraryPhase.Completed), instant(Nov28, 9), Toronto, demoMode = true),
        )
    }

    @Test
    fun `starting while another is Underway needs confirmation`() = runBlocking {
        val repository = InMemoryItineraryRepository()
        val current = night(
            id = "old",
            phase = ItineraryPhase.Underway,
            stops = listOf(
                stop("done", at(Nov28, 18), at(Nov28, 19), status = StopStatus.Confirmed),
                stop("todo", at(Nov28, 20), at(Nov28, 21)),
                stop("gone", at(Nov28, 22), at(Nov28, 23), status = StopStatus.Skipped),
            ),
        )
        repository.saveItinerary(current)
        repository.saveItinerary(night(id = "new", stops = listOf(eventStop)))
        val now = instant(Nov28, 18, 30)

        val first = startItinerary(repository, UID, "new", now, Toronto)
        assertEquals("old", (first as StartResult.NeedsConfirmation).current.id)
        assertEquals(ItineraryPhase.Planning, repository.getItinerary(UID, "new")!!.phase)
        assertEquals(ItineraryPhase.Underway, repository.getItinerary(UID, "old")!!.phase)

        val second = startItinerary(repository, UID, "new", now, Toronto, confirmEndCurrent = true)
        assertEquals("old", (second as StartResult.Started).endedItinerary!!.id)

        val old = repository.getItinerary(UID, "old")!!
        assertEquals(ItineraryPhase.Completed, old.phase)
        assertEquals(
            listOf(StopStatus.Confirmed, StopStatus.Skipped, StopStatus.Skipped),
            old.stops.map { it.status },
        )
        assertEquals(ItineraryPhase.Underway, repository.getItinerary(UID, "new")!!.phase)
        val underway = repository.observeItineraries(UID).first().filter { it.phase == ItineraryPhase.Underway }
        assertEquals(listOf("new"), underway.map { it.id })
    }

    @Test
    fun `a blocked Start does not end the current Itinerary`() = runBlocking {
        val repository = InMemoryItineraryRepository()
        repository.saveItinerary(night(id = "old", phase = ItineraryPhase.Underway))
        repository.saveItinerary(night(id = "new", date = Nov28.plusDays(2), stops = listOf(eventStop)))
        val result = startItinerary(repository, UID, "new", instant(Nov28, 12), Toronto, confirmEndCurrent = true)
        assertEquals(StartResult.Blocked(StartBlock.WrongDate), result)
        assertEquals(ItineraryPhase.Underway, repository.getItinerary(UID, "old")!!.phase)
    }

    @Test
    fun `a missing Itinerary is reported`() = runBlocking {
        assertEquals(
            StartResult.NotFound,
            startItinerary(InMemoryItineraryRepository(), UID, "none", instant(Nov28, 12), Toronto),
        )
    }
}
