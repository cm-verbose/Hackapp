package com.example.hackapp.passport

import com.example.hackapp.data.model.DescriptionSource
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.data.model.LocalizedText
import com.example.hackapp.data.model.Place
import com.example.hackapp.data.model.VenueType
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.Nov28
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.itinerary.StopStatus
import com.example.hackapp.itinerary.UID
import com.example.hackapp.itinerary.at
import com.example.hackapp.itinerary.night
import com.example.hackapp.itinerary.stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StampAwardingTest {
    private val places = mapOf(
        "1" to place(1, "Tim Hortons - Rue Saint-Denis", chain = true),
        "2" to place(2, "Tim Hortons (Berri)", chain = true),
        "3" to place(3, "Café Névé", chain = false),
        "4" to place(4, "Bar Datcha", chain = false, type = VenueType.BAR),
    )
    private val resolver = PlaceStampIdentityResolver({ places[it] }, { if (it == "e1") "Music" else null })
    private val Nov29: LocalDate = Nov28.plusDays(1)

    private fun meal(id: String, place: String, hour: Int, status: StopStatus = StopStatus.Confirmed, day: LocalDate = Nov28) =
        stop(id, at(day, hour), at(day, hour + 1), kind = StopKind.Meal, sourceId = place, status = status)

    private fun event(id: String, event: String, hour: Int, status: StopStatus = StopStatus.Confirmed, day: LocalDate = Nov28) =
        stop(id, at(day, hour), at(day, hour + 1), sourceId = event, status = status)

    private fun award(it: Itinerary, existing: List<PassportStamp> = emptyList()) = awardStamps(it, existing, resolver, 100)

    @Test
    fun `confirmed Stops earn Stamps with the Passport data`() {
        val result = award(night(stops = listOf(event("a", "e1", 19), meal("b", "3", 21))))
        val stamps = result.created
        assertEquals(2, stamps.size)
        val ev = stamps.first { it.kind == StampKind.Event }
        assertEquals("mtl:event:e1", ev.key)
        assertEquals("Stop a", ev.name)
        assertEquals(Nov28, ev.date)
        assertEquals("ville-marie", ev.districtId)
        assertEquals("music", ev.category)
        assertEquals(UID, ev.ownerUid)
        assertEquals("cafe", stamps.first { it.kind == StampKind.Meal }.category)
        assertTrue(result.outcomes.all { it.result is StopStampResult.NewStamp })
    }

    @Test
    fun `planned and skipped Stops never earn a Stamp`() {
        val result = award(night(stops = listOf(
            event("a", "e1", 19, StopStatus.Skipped),
            event("b", "e2", 20, StopStatus.Planned),
            meal("c", "3", 21, StopStatus.Skipped),
        )))
        assertTrue(result.created.isEmpty())
        assertEquals(listOf(StopStampResult.NoStamp), result.outcomes.map { it.result }.distinct())
    }

    @Test
    fun `an Itinerary with no Stops earns nothing`() {
        val result = award(night())
        assertTrue(result.outcomes.isEmpty() && result.created.isEmpty())
    }

    @Test
    fun `running again after a crash creates nothing and keeps the results`() {
        val itinerary = night(stops = listOf(event("a", "e1", 19), meal("b", "3", 21), meal("c", "3", 22, StopStatus.Skipped)))
        val first = award(itinerary)
        val retry = award(itinerary, first.created)
        assertTrue(retry.created.isEmpty())
        assertEquals(first.outcomes, retry.outcomes)
        assertTrue(retry.resultFor("a") is StopStampResult.NewStamp)
        assertEquals(StopStampResult.NoStamp, retry.resultFor("c"))
    }

    @Test
    fun `a partial earlier run is completed without duplicates`() {
        val itinerary = night(stops = listOf(event("a", "e1", 19), meal("b", "3", 21)))
        val partial = award(itinerary).created.filter { it.stopId == "a" }
        val retry = award(itinerary, partial)
        assertEquals(listOf("b"), retry.created.map { it.stopId })
        assertTrue(retry.resultFor("a") is StopStampResult.NewStamp)
    }

    @Test
    fun `a place stamped on an earlier day earns no new Stamp`() {
        val earlier = award(night(id = "old", stops = listOf(meal("a", "3", 20)))).created
        val next = night(id = "new", date = Nov29, stops = listOf(meal("b", "3", 20, day = Nov29)))
        val result = award(next, earlier)
        assertTrue(result.created.isEmpty())
        val outcome = result.resultFor("b") as StopStampResult.AlreadyInPassport
        assertEquals(Nov28, outcome.stamp.date)
    }

    @Test
    fun `an Event attended again earns one Stamp ever`() {
        val earlier = award(night(id = "old", stops = listOf(event("a", "e1", 19)))).created
        val again = award(night(id = "new", date = Nov29, stops = listOf(event("b", "e1", 19, day = Nov29))), earlier)
        assertTrue(again.resultFor("b") is StopStampResult.AlreadyInPassport)
    }

    @Test
    fun `an Event and a Place with the same id are different Stamps`() {
        val result = award(night(stops = listOf(event("a", "3", 19), meal("b", "3", 21))))
        assertEquals(2, result.created.size)
    }

    @Test
    fun `only the first chain location visited earns the Stamp`() {
        val result = award(night(stops = listOf(meal("late", "2", 22), meal("early", "1", 18))))
        assertEquals(listOf("early"), result.created.map { it.stopId })
        assertTrue(result.resultFor("early") is StopStampResult.NewStamp)
        assertTrue(result.resultFor("late") is StopStampResult.AlreadyInPassport)
        assertEquals("Stop early", result.created.single().name)
        assertEquals("mtl:chain:timhortons", result.created.single().key)
    }

    @Test
    fun `a chain stamped earlier blocks other locations later`() {
        val earlier = award(night(id = "old", stops = listOf(meal("a", "1", 20)))).created
        val result = award(night(id = "new", date = Nov29, stops = listOf(meal("b", "2", 20, day = Nov29))), earlier)
        assertTrue(result.resultFor("b") is StopStampResult.AlreadyInPassport)
    }

    @Test
    fun `a skipped first chain location leaves the Stamp for the confirmed one`() {
        val result = award(night(stops = listOf(meal("early", "1", 18, StopStatus.Skipped), meal("late", "2", 22))))
        assertEquals(listOf("late"), result.created.map { it.stopId })
    }

    @Test
    fun `a non-chain Place is not merged by name`() {
        val twin = place(5, "Café Névé", chain = false)
        val r = PlaceStampIdentityResolver({ (places + ("5" to twin))[it] })
        val result = awardStamps(night(stops = listOf(meal("a", "3", 18), meal("b", "5", 20))), emptyList(), r, 1)
        assertEquals(2, result.created.size)
    }

    @Test
    fun `an unknown Place counts as its own Place`() {
        val result = award(night(stops = listOf(meal("a", "99", 18), meal("b", "98", 20))))
        assertEquals(2, result.created.size)
        assertEquals(null, result.created.first().category)
    }

    @Test
    fun `the same Stop twice in one Itinerary earns one Stamp`() {
        val result = award(night(stops = listOf(event("a", "e1", 19), event("b", "e1", 21))))
        assertEquals(1, result.created.size)
        assertTrue(result.resultFor("b") is StopStampResult.AlreadyInPassport)
    }

    @Test
    fun `a Stop after midnight is dated by the Itinerary date`() {
        val after = event("a", "e1", 1, day = Nov29)
        val result = award(night(date = Nov28, stops = listOf(after)))
        assertEquals(Nov28, result.created.single().date)
    }

    @Test
    fun `after midnight Stops are ordered by time, not by list order`() {
        val result = award(night(stops = listOf(meal("late", "2", 1, day = Nov29), meal("early", "1", 22))))
        assertEquals(listOf("early"), result.created.map { it.stopId })
    }

    @Test
    fun `other people's Stamps are ignored`() {
        val theirs = award(night(stops = listOf(event("a", "e1", 19)))).created.map { it.copy(ownerUid = "someone-else") }
        val result = award(night(stops = listOf(event("a", "e1", 19))), theirs)
        assertEquals(1, result.created.size)
    }

    @Test
    fun `chain keys ignore case, accents, branches and brackets`() {
        assertEquals("cafeneve", chainKey("Café Névé (Mile End)"))
        assertEquals("timhortons", chainKey("TIM HORTONS – Berri"))
        assertEquals("", chainKey("()"))
    }

    @Test
    fun `a chain with an empty brand key falls back to its own Place`() {
        val odd = place(7, "()", chain = true)
        val r = PlaceStampIdentityResolver({ if (it == "7") odd else null })
        assertEquals("place:7", r.identify(meal("a", "7", 18), "mtl").key)
    }

    private fun place(id: Long, name: String, chain: Boolean, type: VenueType = VenueType.CAFE) = Place(
        id = id, name = name, address = "", location = LatLng(45.5, -73.6), borough = null, venueType = type,
        cuisines = emptyList(), dietary = emptySet(), isChain = chain, price = null,
        description = LocalizedText(null, null), descriptionSource = DescriptionSource.GENERATED,
        rating = null, ratingCount = null, openingHours = null, photoRef = null, googlePlaceId = null,
    )
}
