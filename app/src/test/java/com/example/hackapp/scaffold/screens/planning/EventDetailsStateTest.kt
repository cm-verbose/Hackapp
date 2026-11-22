package com.example.hackapp.scaffold.screens.planning

import com.example.hackapp.data.Cost
import com.example.hackapp.data.CostStatus
import com.example.hackapp.data.Event
import com.example.hackapp.data.TargetAudience
import com.example.hackapp.itinerary.InMemoryTicketPriceRepository
import com.example.hackapp.itinerary.ReachableShowtime
import com.example.hackapp.itinerary.ShowtimeKey
import com.example.hackapp.itinerary.TicketPriceRepository
import com.example.hackapp.itinerary.TicketPriceSummary
import com.example.hackapp.itinerary.Nov28
import com.example.hackapp.itinerary.Toronto
import com.example.hackapp.itinerary.UID
import com.example.hackapp.itinerary.at
import com.example.hackapp.itinerary.event
import com.example.hackapp.itinerary.night
import com.example.hackapp.itinerary.showtimeKey
import com.example.hackapp.itinerary.startTime
import com.example.hackapp.itinerary.suggestDeparture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDetailsStateTest {
    private val itinerary = night()

    private fun showtime(e: Event) = ReachableShowtime(
        e, e.startTime()!!.atZoneSameInstant(Toronto), suggestDeparture(e, itinerary, Toronto)!!, null,
    )

    private fun e(id: String, hour: Int = 19, cost: Cost = Cost(CostStatus.UNKNOWN), end: String? = null) =
        event(id, start = at(Nov28, hour).toString(), title = "Show", venue = "Hall", cost = cost, end = end)

    private fun holder(
        events: List<Event> = listOf(e("a")),
        uid: String? = UID,
        repo: TicketPriceRepository = InMemoryTicketPriceRepository(),
    ) = EventDetailsHolder(events.map(::showtime), null, Toronto, uid, repo)

    @Test
    fun `shows the basic fields`() {
        val ev = event(
            "a", start = at(Nov28, 19).toString(), title = "Show", venue = "Hall",
            audience = TargetAudience("Teens and up", 13, null),
        ).copy(description = "About", url = "https://tickets.example")
        val s = holder(listOf(ev)).state.value
        assertEquals("Show", s.title)
        assertEquals("music", s.type)
        assertEquals("Hall", s.venueName)
        assertEquals(EventAudience("Teens and up", 13, null), s.audience)
        assertEquals("About", s.description)
        assertEquals("https://tickets.example", s.ticketUrl)
        assertTrue(s.endTimeUnpublished)
        assertNull(s.publishedEnd)
        assertTrue(s.canAdd)
        assertFalse(s.hasRepeatedShowtimes)
    }

    @Test
    fun `repeated showtimes are selectable`() {
        val h = holder(listOf(e("a", 17), e("b", 21)))
        assertTrue(h.state.value.hasRepeatedShowtimes)
        assertEquals("a", h.state.value.eventId)
        h.selectShowtime("b")
        assertEquals("b", h.state.value.eventId)
        assertEquals(listOf(false, true), h.state.value.showtimes.map { it.selected })
        h.selectShowtime("nope")
        assertEquals("b", h.state.value.eventId)
    }

    @Test
    fun `price is free, median, published or unknown`() = runBlocking {
        assertEquals(EventPrice.Unknown, holder().also { it.load() }.state.value.price)
        assertEquals(EventPrice.Free, holder(listOf(e("a", cost = Cost(CostStatus.FREE)))).state.value.price)
        val paid = Cost(CostStatus.PAID, minPrice = 10.0, maxPrice = 30.0)
        assertEquals(EventPrice.Published(10.0, 30.0), holder(listOf(e("a", cost = paid))).state.value.price)

        val repo = InMemoryTicketPriceRepository()
        val key = e("a").showtimeKey()
        repo.saveReport("u1", key, 10.0)
        repo.saveReport("u2", key, 20.0)
        repo.saveReport("u3", key, 40.0)
        val h = holder(listOf(e("a", cost = paid)), uid = null, repo = repo)
        h.load()
        assertEquals(EventPrice.UserMedian(20.0, 3), h.state.value.price)
        assertFalse(h.state.value.ticketPrice.signedIn)
    }

    @Test
    fun `input is validated`() {
        val h = holder()
        fun check(text: String) = h.run { setTicketPriceInput(text); state.value.ticketPrice }
        assertNull(check("").inputError)
        assertNull(check("0").inputError)
        assertNull(check("$12,50").inputError)
        assertNull(check("500").inputError)
        assertEquals(TicketPriceInputError.OutOfRange, check("500.01").inputError)
        assertEquals(TicketPriceInputError.OutOfRange, check("-1").inputError)
        assertEquals(TicketPriceInputError.NotANumber, check("abc").inputError)
        assertEquals(TicketPriceInputError.NotANumber, check("NaN").inputError)
        assertEquals(TicketPriceInputError.NotANumber, check("1.234").inputError)
        assertEquals(TicketPriceInputError.NotANumber, check("1e3").inputError)
    }

    @Test
    fun `saves one editable report that applies to all showtimes`() = runBlocking {
        val repo = InMemoryTicketPriceRepository()
        val h = holder(listOf(e("a", 17), e("b", 21)), repo = repo)
        h.load()
        h.setTicketPriceInput("25")
        assertTrue(h.state.value.ticketPrice.canSave)
        assertTrue(h.saveTicketPrice())
        assertEquals(TicketPriceStatus.Saved, h.state.value.ticketPrice.status)
        assertEquals(25.0, repo.getOwnReport(UID, e("b").showtimeKey())!!, 0.0)
        assertEquals(EventPrice.UserMedian(25.0, 1), h.state.value.price)
        assertFalse(h.state.value.ticketPrice.canSave)

        h.setTicketPriceInput("30")
        assertTrue(h.saveTicketPrice())
        assertEquals(EventPrice.UserMedian(30.0, 1), h.state.value.price)
    }

    @Test
    fun `loads the person's own report`() = runBlocking {
        val repo = InMemoryTicketPriceRepository()
        repo.saveReport(UID, e("a").showtimeKey(), 12.5)
        val h = holder(repo = repo)
        assertEquals(TicketPriceStatus.Loading, h.state.value.ticketPrice.status)
        h.load()
        assertEquals(12.5, h.state.value.ticketPrice.ownReportCad)
        assertEquals("12.50", h.state.value.ticketPrice.input)
        assertEquals(TicketPriceStatus.Idle, h.state.value.ticketPrice.status)
    }

    @Test
    fun `invalid input and signed out people cannot save`() = runBlocking {
        val repo = InMemoryTicketPriceRepository()
        val h = holder(repo = repo)
        h.load()
        h.setTicketPriceInput("900")
        assertFalse(h.saveTicketPrice())
        val out = holder(uid = null, repo = repo)
        out.load()
        out.setTicketPriceInput("10")
        assertFalse(out.saveTicketPrice())
        assertNull(repo.getSummary(e("a").showtimeKey()))
    }

    private class FailingRepo(val failLoad: Boolean, val failSave: Boolean) : TicketPriceRepository {
        override fun observeSummary(key: ShowtimeKey): Flow<TicketPriceSummary?> = emptyFlow()
        override suspend fun getSummary(key: ShowtimeKey): TicketPriceSummary? =
            if (failLoad) error("offline") else null
        override suspend fun getOwnReport(uid: String, key: ShowtimeKey): Double? = null
        override suspend fun saveReport(uid: String, key: ShowtimeKey, priceCad: Double): Boolean =
            if (failSave) error("offline") else true
        override suspend fun deleteReport(uid: String, key: ShowtimeKey) = Unit
    }

    @Test
    fun `load and save failures become states and adding still works`() = runBlocking {
        val h = holder(repo = FailingRepo(failLoad = true, failSave = true))
        h.load()
        assertEquals(TicketPriceStatus.LoadFailed, h.state.value.ticketPrice.status)
        assertEquals(EventPrice.Unknown, h.state.value.price)
        assertTrue(h.addToItinerary() is AddToItineraryAction.ChooseDeparture)

        val h2 = holder(repo = FailingRepo(failLoad = false, failSave = true))
        h2.load()
        h2.setTicketPriceInput("10")
        assertFalse(h2.saveTicketPrice())
        assertEquals(TicketPriceStatus.SaveFailed, h2.state.value.ticketPrice.status)
        assertEquals("10", h2.state.value.ticketPrice.input)
        assertNull(h2.addToItinerary())
    }

    @Test
    fun `add without a published end asks for a departure and carries the own price`() = runBlocking {
        val h = holder()
        h.load()
        h.setTicketPriceInput("18")
        val action = h.addToItinerary() as AddToItineraryAction.ChooseDeparture
        assertEquals("a", action.event.id)
        assertEquals(18.0, action.ticketPriceCad)
    }

    @Test
    fun `add with a published end builds the Stop`() = runBlocking {
        val h = holder(listOf(e("a", end = at(Nov28, 21).toString())))
        h.load()
        val stop = (h.addToItinerary() as AddToItineraryAction.Add).stop
        assertEquals("event-a", stop.id)
        assertTrue(stop.hasPublishedEnd)
        assertEquals(at(Nov28, 21).toInstant(), stop.plannedDeparture.toInstant())
        assertNull(stop.ticketPriceCad)
    }

    @Test
    fun `an Event that cannot become a Stop cannot be added`() = runBlocking {
        val noCoords = event("a", start = at(Nov28, 19).toString(), latitude = null)
        val h = holder(listOf(noCoords))
        assertFalse(h.state.value.canAdd)
        assertNull(h.addToItinerary())
    }
}
