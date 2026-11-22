package com.example.hackapp.scaffold.screens.planning

import com.example.hackapp.data.Cost
import com.example.hackapp.data.CostStatus
import com.example.hackapp.data.Event
import com.example.hackapp.data.model.CuisineGroup
import com.example.hackapp.data.model.MealSlot
import com.example.hackapp.data.model.Place
import com.example.hackapp.data.model.PriceBracket
import com.example.hackapp.data.model.PriceRange
import com.example.hackapp.data.model.VenueType
import com.example.hackapp.data.repository.EventQuery
import com.example.hackapp.data.repository.EventRepository
import com.example.hackapp.data.repository.PlaceQuery
import com.example.hackapp.data.repository.PlaceRepository
import com.example.hackapp.itinerary.HERE
import com.example.hackapp.itinerary.InMemoryItineraryRepository
import com.example.hackapp.itinerary.InMemoryTicketPriceRepository
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.MealBlock
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.itinerary.NEXT
import com.example.hackapp.itinerary.Nov28
import com.example.hackapp.itinerary.PlanningStep
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.itinerary.Toronto
import com.example.hackapp.itinerary.UID
import com.example.hackapp.itinerary.at
import com.example.hackapp.itinerary.event
import com.example.hackapp.itinerary.fixedLegs
import com.example.hackapp.itinerary.instant
import com.example.hackapp.itinerary.night
import com.example.hackapp.itinerary.place
import com.example.hackapp.profile.ProfileRepository
import com.example.hackapp.profile.Profile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

private class FakeEvents(private val events: List<Event>) : EventRepository {
    override suspend fun search(query: EventQuery) = events
    override suspend fun getEvent(id: String) = events.firstOrNull { it.id == id }
    override suspend fun countByDistrict(query: EventQuery) = emptyMap<String, Int>()
}

private class FakePlaces(private val places: List<Place>) : PlaceRepository {
    override suspend fun search(query: PlaceQuery) = places
    override suspend fun getPlace(id: Long) = places.firstOrNull { it.id == id }
    override suspend fun allPlaces() = places
    override suspend fun cuisineGroups(): List<CuisineGroup> = emptyList()
    override suspend fun priceBrackets(): List<PriceBracket> = emptyList()
    override suspend fun venueTypesFor(slot: MealSlot): Set<VenueType> = emptySet()
}

private object NoProfile : ProfileRepository {
    override suspend fun getProfile(uid: String): Profile? = null
    override suspend fun saveProfile(uid: String, profile: Profile) = Unit
}

class StopSelectionSessionTest {
    private val dinner = MealBlock("meal-dinner", MealKind.Dinner, LocalTime.of(20, 0))
    private val repo = InMemoryItineraryRepository()

    private fun session(
        itinerary: Itinerary,
        events: List<Event> = emptyList(),
        places: List<Place> = emptyList(),
        saved: Boolean = true,
    ): StopSelectionSession {
        if (saved) runBlocking { repo.saveItinerary(itinerary) }
        return StopSelectionSession(
            itineraryId = itinerary.id,
            uid = { UID },
            itineraries = repo,
            profiles = NoProfile,
            eventRepository = FakeEvents(events),
            placeRepository = FakePlaces(places),
            tickets = InMemoryTicketPriceRepository(),
            estimator = fixedLegs(),
            zone = Toronto,
            nowMillis = { instant(Nov28, 8).toEpochMilli() },
        )
    }

    private fun ids(state: PlanningStopSelectionUiState) =
        state.groups.flatMap { h -> h.types.flatMap { t -> t.cards } }.map { it.showtimes.first().event.id }

    private val early = event("early", start = at(Nov28, 18, 30).toString(), end = at(Nov28, 19, 30).toString(), type = "music")
    private val noEnd = event("noend", start = at(Nov28, 18).toString(), type = "music")
    private val other = event("other", start = at(Nov28, 18, 30).toString(), type = "other")
    private val afterMeal = event("after", start = at(Nov28, 22).toString(), end = at(Nov28, 23).toString())
    private val inNext = event("next", start = at(Nov28, 22).toString(), end = at(Nov28, 23).toString(), district = NEXT)

    @Test
    fun `loads the first District gap, hides other Events and those after the block`() = runBlocking {
        val s = session(night(blocks = listOf(dinner)), listOf(early, other, afterMeal, inNext))
        s.load()
        val state = s.state.value
        assertFalse(state.loading)
        assertEquals(listOf("early"), ids(state))
        assertEquals(HERE, state.districtId)
        assertEquals(1, state.districtNumber)
        assertTrue(state.hasNextDistrict)
        assertFalse(state.canFinish)
        assertEquals(at(Nov28, 20).toInstant(), state.gapEnd!!.toInstant())
        val prompt = state.mealPrompt!!
        assertEquals(MealKind.Dinner, prompt.kind)
        assertFalse(prompt.reached)
    }

    @Test
    fun `adding an Event with a published end saves it and refreshes from its departure`() = runBlocking {
        val second = event("second", start = at(Nov28, 19, 50).toString(), end = at(Nov28, 19, 55).toString())
        val s = session(night(blocks = listOf(dinner)), listOf(early, second))
        s.load()
        s.openEvent(s.state.value.groups.first().types.first().cards.first { it.title == early.title })
        s.addToItinerary()
        val state = s.state.value
        assertNull(state.sheet)
        assertEquals(listOf("event-early"), state.stops.map { it.id })
        assertTrue(state.canFinish)
        assertEquals(listOf("second"), ids(state))
        assertEquals(at(Nov28, 19, 30).toInstant(), state.gapStart!!.toInstant())
        val saved = repo.getItinerary(UID, "it-1")!!
        assertEquals(PlanningStep.Stops, saved.step)
        assertEquals(listOf("event-early"), saved.stops.map { it.id })
    }

    @Test
    fun `an Event without an end goes through Planned Departure with latestDeparture`() = runBlocking {
        val s = session(night(blocks = listOf(dinner)), listOf(noEnd))
        s.load()
        s.openEvent(s.state.value.groups.first().types.first().cards.first())
        s.addToItinerary()
        val sheet = s.state.value.sheet as SelectionSheet.PlannedDeparture
        assertEquals(at(Nov28, 20).toInstant(), sheet.holder.state.value.latestDeparture.toInstant())
        s.setDeparture(LocalTime.of(19, 45))
        s.confirmDeparture()
        val stop = s.state.value.stops.single()
        assertEquals(at(Nov28, 19, 45).toInstant(), stop.plannedDeparture.toInstant())
        assertFalse(stop.hasPublishedEnd)
    }

    @Test
    fun `skipping the Meal frees the block and its Events reappear`() = runBlocking {
        val s = session(night(blocks = listOf(dinner)), listOf(afterMeal))
        s.load()
        assertTrue(ids(s.state.value).isEmpty())
        assertTrue(s.state.value.mealPrompt!!.reached)
        s.skipMeal()
        assertEquals(listOf("after"), ids(s.state.value))
        assertNull(s.state.value.mealPrompt)
        assertTrue(repo.getItinerary(UID, "it-1")!!.mealBlocks.single().skipped)
    }

    @Test
    fun `choosing a Meal here keeps the District and lists later Events`() = runBlocking {
        val s = session(night(blocks = listOf(dinner)), listOf(afterMeal), listOf(place(2, district = HERE)))
        s.load()
        s.openMealPicker()
        s.chooseMeal(2)
        val state = s.state.value
        assertEquals(HERE, state.districtId)
        assertNull(state.mealPrompt)
        assertEquals(listOf("after"), ids(state))
        assertEquals(StopKind.Meal, state.stops.single().kind)
    }

    @Test
    fun `a Meal in the next District moves planning there and Back undoes it`() = runBlocking {
        val s = session(night(blocks = listOf(dinner)), listOf(afterMeal, inNext), listOf(place(1, district = NEXT)))
        s.load()
        s.openMealPicker()
        val picker = (s.state.value.sheet as SelectionSheet.MealPicker).holder.state.value
        assertEquals(listOf(MealTab.Here, MealTab.NextDistrict), picker.tabs)
        s.chooseMeal(1)
        var state = s.state.value
        assertEquals(NEXT, state.districtId)
        assertEquals(2, state.districtNumber)
        assertFalse(state.hasNextDistrict)
        assertEquals(listOf("next"), ids(state))
        assertEquals(NEXT, repo.getItinerary(UID, "it-1")!!.planningDistrictId)

        s.back()
        state = s.state.value
        assertEquals(HERE, state.districtId)
        assertTrue(state.stops.isEmpty())
        assertNotNull(state.mealPrompt)
        assertEquals(listOf(HERE, NEXT), state.districtIds)
        assertNull(state.leaveId)
    }

    @Test
    fun `Back after more changes keeps the Stops and the Meal`() = runBlocking {
        val s = session(night(blocks = listOf(dinner)), listOf(afterMeal, inNext), listOf(place(1, district = NEXT)))
        s.load()
        s.openMealPicker()
        s.chooseMeal(1)
        s.openEvent(s.state.value.groups.first().types.first().cards.first())
        s.addToItinerary()
        assertEquals(2, s.state.value.stops.size)
        s.back()
        assertEquals(HERE, s.state.value.districtId)
        assertEquals(2, s.state.value.stops.size)
        assertEquals(listOf(HERE, NEXT), s.state.value.districtIds)
    }

    @Test
    fun `Next borough drops a District with no Stop and the last District shows only Finish`() = runBlocking {
        val s = session(night(), listOf(inNext))
        s.load()
        s.nextDistrict()
        val state = s.state.value
        assertEquals(NEXT, state.districtId)
        assertEquals(listOf(NEXT), state.districtIds)
        assertFalse(state.hasNextDistrict)
        assertEquals(listOf(NEXT), repo.getItinerary(UID, "it-1")!!.districtIds)
    }

    @Test
    fun `Next borough keeps a District that has Stops`() = runBlocking {
        val s = session(night(), listOf(early, inNext))
        s.load()
        s.openEvent(s.state.value.groups.first().types.first().cards.first())
        s.addToItinerary()
        s.nextDistrict()
        assertEquals(listOf(HERE, NEXT), s.state.value.districtIds)
        assertEquals(NEXT, s.state.value.districtId)
    }

    @Test
    fun `Finish is ignored without an Event, then goes to Review and prunes empty Districts`() = runBlocking {
        val s = session(night(), listOf(early))
        s.load()
        s.finish()
        assertNull(s.state.value.reviewId)
        s.openEvent(s.state.value.groups.first().types.first().cards.first())
        s.addToItinerary()
        s.finish()
        assertEquals("it-1", s.state.value.reviewId)
        val saved = repo.getItinerary(UID, "it-1")!!
        assertEquals(PlanningStep.Review, saved.step)
        assertEquals(listOf(HERE), saved.districtIds)
        s.onNavigated()
        assertNull(s.state.value.reviewId)
    }

    @Test
    fun `Back at the first District leaves the step without dropping it`() = runBlocking {
        val s = session(night())
        s.load()
        s.back()
        assertEquals("it-1", s.state.value.leaveId)
        assertEquals(listOf(HERE, NEXT), repo.getItinerary(UID, "it-1")!!.districtIds)
    }

    @Test
    fun `a reopened draft resumes with the District, Stops and skipped blocks`() = runBlocking {
        val first = session(night(blocks = listOf(dinner)), listOf(early, inNext))
        first.load()
        first.openEvent(first.state.value.groups.first().types.first().cards.first())
        first.addToItinerary()
        first.skipMeal()
        first.nextDistrict()
        val again = session(repo.getItinerary(UID, "it-1")!!, listOf(early, inNext), saved = false)
        again.load()
        val state = again.state.value
        assertEquals(NEXT, state.districtId)
        assertEquals(listOf("event-early"), state.stops.map { it.id })
        assertNull(state.mealPrompt)
        assertEquals(listOf("next"), ids(state))
    }

    @Test
    fun `the running total counts Event prices and the top of the Meal range, and only warns`() = runBlocking {
        val paid = event("paid", start = at(Nov28, 18, 30).toString(), end = at(Nov28, 19).toString(), cost = Cost(CostStatus.PAID, 10.0, 80.0))
        val unknown = event("unk", start = at(Nov28, 19, 30).toString(), end = at(Nov28, 19, 45).toString())
        val withBudget = night(blocks = listOf(dinner.copy(budget = com.example.hackapp.profile.BudgetBracket.Under20)))
            .copy(eventBudget = com.example.hackapp.profile.BudgetBracket.Under50)
        val s = session(withBudget, listOf(paid, unknown), listOf(place(2, price = PriceRange(10, 20, estimated = false))))
        s.load()
        for (id in listOf("paid", "unk")) {
            val card = s.state.value.groups.flatMap { it.types }.flatMap { it.cards }.first { it.showtimes.first().event.id == id }
            s.openEvent(card)
            s.addToItinerary()
        }
        s.openMealPicker()
        s.chooseMeal(2)
        val budget = s.state.value.budget!!
        assertEquals(80.0, budget.eventsCad, 0.0)
        assertEquals(20.0, budget.mealsCad, 0.0)
        assertEquals(1, budget.unknownPriceCount)
        assertTrue(budget.hasUnknownPrices)
        assertEquals(70, budget.totalBudgetCad)
        assertTrue(budget.isOverBudget)
        assertTrue(s.state.value.canFinish)
    }

    @Test
    fun `a failed save is reported and can be retried`() = runBlocking {
        val failing = object : com.example.hackapp.itinerary.ItineraryRepository by repo {
            var fail = false
            override suspend fun saveItinerary(itinerary: Itinerary) {
                if (fail) error("offline") else repo.saveItinerary(itinerary)
            }
        }
        val itinerary = night()
        repo.saveItinerary(itinerary)
        val s = StopSelectionSession("it-1", { UID }, failing, NoProfile, FakeEvents(listOf(early)), FakePlaces(emptyList()),
            InMemoryTicketPriceRepository(), fixedLegs(), Toronto, { instant(Nov28, 8).toEpochMilli() })
        s.load()
        failing.fail = true
        s.openEvent(s.state.value.groups.first().types.first().cards.first())
        s.addToItinerary()
        assertTrue(s.state.value.saveFailed)
        assertEquals(1, s.state.value.stops.size)
        failing.fail = false
        s.retrySave()
        assertFalse(s.state.value.saveFailed)
        assertEquals(1, repo.getItinerary(UID, "it-1")!!.stops.size)
    }

    @Test
    fun `other Events are hidden and empty hours dropped`() {
        val other = event("o", type = "other", start = at(Nov28, 19).toString())
        val music = event("m", type = "music", start = at(Nov28, 20).toString())
        val search = com.example.hackapp.itinerary.EventSearch(night(), listOf(other, music), com.example.hackapp.itinerary.context(instant(Nov28, 8)), HERE)
        val groups = hideOtherEvents(com.example.hackapp.itinerary.PlanningEngine(fixedLegs()).groupedEvents(search))
        assertEquals(listOf("m"), groups.flatMap { h -> h.types.flatMap { t -> t.cards } }.map { it.showtimes.first().event.id })
        assertEquals(1, groups.size)
    }

    private class FailingSaves(private val inner: InMemoryTicketPriceRepository) : com.example.hackapp.itinerary.TicketPriceRepository by inner {
        override suspend fun saveReport(uid: String, key: com.example.hackapp.itinerary.ShowtimeKey, priceCad: Double): Boolean = error("offline")
    }

    private suspend fun failedPriceSession(events: List<Event>): StopSelectionSession {
        runBlocking { repo.saveItinerary(night(blocks = listOf(dinner))) }
        val s = StopSelectionSession("it-1", { UID }, repo, NoProfile, FakeEvents(events), FakePlaces(emptyList()),
            FailingSaves(InMemoryTicketPriceRepository()), fixedLegs(), Toronto, { instant(Nov28, 8).toEpochMilli() })
        s.load()
        s.openEvent(s.state.value.groups.first().types.first().cards.first())
        s.setTicketPriceInput("20")
        s.addToItinerary()
        return s
    }

    @Test
    fun `add without price adds the Event after a failed price save`() = runBlocking {
        val s = failedPriceSession(listOf(early))
        assertTrue(s.state.value.stops.isEmpty())
        s.addWithoutPrice()
        assertNull(s.state.value.sheet)
        assertEquals(listOf("event-early"), s.state.value.stops.map { it.id })
        assertNull(s.state.value.stops.single().ticketPriceCad)
    }

    @Test
    fun `add without price opens Planned Departure when the Event has no end`() = runBlocking {
        val s = failedPriceSession(listOf(noEnd))
        s.addWithoutPrice()
        assertTrue(s.state.value.sheet is SelectionSheet.PlannedDeparture)
        assertTrue(s.state.value.stops.isEmpty())
    }

    @Test
    fun `add without price keeps the arrival of an in-progress drop-in`() = runBlocking {
        val allDay = event("all-day", start = at(Nov28, 10).toString(), end = at(Nov28, 21).toString(), type = "exhibition")
        val s = failedPriceSession(listOf(allDay))
        s.addWithoutPrice()
        assertTrue(s.state.value.sheet is SelectionSheet.PlannedDeparture)
        s.confirmDeparture()
        val stop = s.state.value.stops.single()
        assertEquals(at(Nov28, 18).toInstant(), stop.start.toInstant())
        assertEquals(at(Nov28, 19).toInstant(), stop.plannedDeparture.toInstant())
        assertTrue(stop.hasPublishedEnd)
    }

    @Test
    fun `a drop-in opens Planned Departure from the arrival and can be adjusted up to its limit`() = runBlocking {
        val allDay = event("all-day", start = at(Nov28, 10).toString(), end = at(Nov28, 21).toString(), type = "exhibition")
        val s = session(night(blocks = listOf(dinner)), listOf(allDay))
        s.load()
        s.openEvent(s.state.value.groups.first().types.first().cards.first())
        s.addToItinerary()
        val sheet = s.state.value.sheet as SelectionSheet.PlannedDeparture
        val departure = sheet.holder.state.value
        assertEquals(at(Nov28, 18).toInstant(), departure.earliestDeparture.toInstant())
        assertEquals(at(Nov28, 19).toInstant(), departure.departure.toInstant())
        assertEquals(at(Nov28, 20).toInstant(), departure.latestDeparture.toInstant())
        assertEquals(60, departure.stayMinutes)
        assertTrue(departure.isTypicalStay)
        assertTrue(departure.hasPublishedEnd)
        s.setDeparture(LocalTime.of(19, 45))
        s.confirmDeparture()
        val stop = s.state.value.stops.single()
        assertEquals(at(Nov28, 18).toInstant(), stop.start.toInstant())
        assertEquals(at(Nov28, 19, 45).toInstant(), stop.plannedDeparture.toInstant())
    }

    @Test
    fun `add without price does nothing when no save failed`() = runBlocking {
        val s = session(night(blocks = listOf(dinner)), listOf(early))
        s.load()
        s.openEvent(s.state.value.groups.first().types.first().cards.first())
        s.addWithoutPrice()
        assertTrue(s.state.value.sheet is SelectionSheet.EventDetails)
        assertTrue(s.state.value.stops.isEmpty())
        val none = StopSelectionSession("it-1", { UID }, repo, NoProfile, FakeEvents(emptyList()), FakePlaces(emptyList()),
            InMemoryTicketPriceRepository(), fixedLegs(), Toronto)
        none.addWithoutPrice()
    }

    @Test
    fun `the Meal prompt is reached when only drop-in cards remain`() = runBlocking {
        val allDay = event("all-day", start = at(Nov28, 10).toString(), end = at(Nov28, 21).toString(), type = "exhibition")
        val onlyDropIn = session(night(blocks = listOf(dinner)), listOf(allDay))
        onlyDropIn.load()
        assertEquals(listOf("all-day"), ids(onlyDropIn.state.value))
        assertTrue(onlyDropIn.state.value.mealPrompt!!.reached)
        val mixed = StopSelectionSession("it-1", { UID }, repo, NoProfile, FakeEvents(listOf(allDay, early)), FakePlaces(emptyList()),
            InMemoryTicketPriceRepository(), fixedLegs(), Toronto, { instant(Nov28, 8).toEpochMilli() })
        mixed.load()
        assertFalse(mixed.state.value.mealPrompt!!.reached)
    }

    @Test
    fun `an unreadable draft is a load error`() = runBlocking {
        val s = session(night(), saved = false)
        s.load()
        assertTrue(s.state.value.loadFailed)
    }
}
