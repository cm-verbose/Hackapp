package com.example.hackapp.passport

import com.example.hackapp.data.model.DescriptionSource
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.data.model.LocalizedText
import com.example.hackapp.data.model.Place
import com.example.hackapp.data.model.VenueType
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.Nov28
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.itinerary.StopStatus
import com.example.hackapp.itinerary.UID
import com.example.hackapp.itinerary.at
import com.example.hackapp.itinerary.night
import com.example.hackapp.itinerary.stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StampAwardingTest {
    private val places = mapOf(
        "1" to place(1, "La Belle Province - Rue Saint-Denis", chain = true),
        "2" to place(2, "Restaurant La Belle Province (Berri)", chain = true),
        "3" to place(3, "Café Névé", chain = false),
        "4" to place(4, "Bar Datcha", chain = false, type = VenueType.BAR),
    )
    private val resolver = PlaceStampIdentityResolver({ places[it] }, { if (it == "e1") "Music" else null })
    private val Nov29: LocalDate = Nov28.plusDays(1)

    private fun meal(id: String, place: String, hour: Int, status: StopStatus = StopStatus.Confirmed, day: LocalDate = Nov28) =
        stop(id, at(day, hour), at(day, hour + 1), kind = StopKind.Meal, sourceId = place, status = status)

    private fun event(id: String, event: String, hour: Int, status: StopStatus = StopStatus.Confirmed, day: LocalDate = Nov28) =
        stop(id, at(day, hour), at(day, hour + 1), sourceId = event, status = status).let { it.copy(name = "Event $event") }

    private fun completed(
        id: String = "it-1",
        date: LocalDate = Nov28,
        stops: List<com.example.hackapp.itinerary.Stop> = emptyList(),
        phase: ItineraryPhase = ItineraryPhase.Completed,
    ) = night(id = id, date = date, stops = stops, phase = phase)

    private fun award(it: Itinerary, existing: List<PassportStamp> = emptyList()) = awardStamps(it, existing, resolver, 100)

    @Test
    fun `confirmed Stops earn Stamps with the Passport data`() {
        val result = award(completed(stops = listOf(event("a", "e1", 19), meal("b", "3", 21))))
        val stamps = result.created
        assertEquals(2, stamps.size)
        val ev = stamps.first { it.kind == StampKind.Event }
        assertEquals("mtl:event:evente1:45.500:-73.600", ev.key)
        assertEquals("Event e1", ev.name)
        assertEquals(Nov28, ev.date)
        assertEquals("ville-marie", ev.districtId)
        assertEquals("music", ev.category)
        assertEquals(UID, ev.ownerUid)
        assertEquals("cafe", stamps.first { it.kind == StampKind.Meal }.category)
        assertTrue(result.outcomes.all { it.result is StopStampResult.NewStamp })
    }

    @Test
    fun `planned and skipped Stops never earn a Stamp`() {
        val result = award(completed(stops = listOf(
            event("a", "e1", 19, StopStatus.Skipped),
            event("b", "e2", 20, StopStatus.Planned),
            meal("c", "3", 21, StopStatus.Skipped),
        )))
        assertTrue(result.created.isEmpty())
        assertEquals(listOf(StopStampResult.NoStamp), result.outcomes.map { it.result }.distinct())
    }

    @Test
    fun `an Itinerary with no Stops earns nothing`() {
        val result = award(completed())
        assertTrue(result.outcomes.isEmpty() && result.created.isEmpty())
    }

    @Test
    fun `running again after a crash creates nothing and keeps the results`() {
        val itinerary = completed(stops = listOf(event("a", "e1", 19), meal("b", "3", 21), meal("c", "3", 22, StopStatus.Skipped)))
        val first = award(itinerary)
        val retry = award(itinerary, first.created)
        assertTrue(retry.created.isEmpty())
        assertEquals(first.outcomes, retry.outcomes)
        assertTrue(retry.resultFor("a") is StopStampResult.NewStamp)
        assertEquals(StopStampResult.NoStamp, retry.resultFor("c"))
    }

    @Test
    fun `a partial earlier run is completed without duplicates`() {
        val itinerary = completed(stops = listOf(event("a", "e1", 19), meal("b", "3", 21)))
        val partial = award(itinerary).created.filter { it.stopId == "a" }
        val retry = award(itinerary, partial)
        assertEquals(listOf("b"), retry.created.map { it.stopId })
        assertTrue(retry.resultFor("a") is StopStampResult.NewStamp)
    }

    @Test
    fun `a place stamped on an earlier day earns no new Stamp`() {
        val earlier = award(completed(id = "old", stops = listOf(meal("a", "3", 20)))).created
        val next = completed(id = "new", date = Nov29, stops = listOf(meal("b", "3", 20, day = Nov29)))
        val result = award(next, earlier)
        assertTrue(result.created.isEmpty())
        val outcome = result.resultFor("b") as StopStampResult.AlreadyInPassport
        assertEquals(Nov28, outcome.stamp.date)
    }

    @Test
    fun `an Event attended again earns one Stamp ever`() {
        val earlier = award(completed(id = "old", stops = listOf(event("a", "e1", 19)))).created
        val again = award(completed(id = "new", date = Nov29, stops = listOf(event("b", "e1", 19, day = Nov29))), earlier)
        assertTrue(again.resultFor("b") is StopStampResult.AlreadyInPassport)
    }

    @Test
    fun `an Event and a Place with the same id are different Stamps`() {
        val result = award(completed(stops = listOf(event("a", "3", 19), meal("b", "3", 21))))
        assertEquals(2, result.created.size)
    }

    @Test
    fun `only the first chain location visited earns the Stamp`() {
        val result = award(completed(stops = listOf(meal("late", "2", 22), meal("early", "1", 18))))
        assertEquals(listOf("early"), result.created.map { it.stopId })
        assertTrue(result.resultFor("early") is StopStampResult.NewStamp)
        assertTrue(result.resultFor("late") is StopStampResult.AlreadyInPassport)
        assertEquals("Stop early", result.created.single().name)
        assertEquals("mtl:chain:labelleprovince", result.created.single().key)
    }

    @Test
    fun `a chain stamped earlier blocks other locations later`() {
        val earlier = award(completed(id = "old", stops = listOf(meal("a", "1", 20)))).created
        val result = award(completed(id = "new", date = Nov29, stops = listOf(meal("b", "2", 20, day = Nov29))), earlier)
        assertTrue(result.resultFor("b") is StopStampResult.AlreadyInPassport)
    }

    @Test
    fun `a skipped first chain location leaves the Stamp for the confirmed one`() {
        val result = award(completed(stops = listOf(meal("early", "1", 18, StopStatus.Skipped), meal("late", "2", 22))))
        assertEquals(listOf("late"), result.created.map { it.stopId })
    }

    @Test
    fun `a non-chain Place is not merged by name`() {
        val twin = place(5, "Café Névé", chain = false)
        val r = PlaceStampIdentityResolver({ (places + ("5" to twin))[it] })
        val result = awardStamps(completed(stops = listOf(meal("a", "3", 18), meal("b", "5", 20))), emptyList(), r, 1)
        assertEquals(2, result.created.size)
    }

    @Test
    fun `an unknown Place counts as its own Place`() {
        val result = award(completed(stops = listOf(meal("a", "99", 18), meal("b", "98", 20))))
        assertEquals(2, result.created.size)
        assertEquals(null, result.created.first().category)
    }

    @Test
    fun `the same Stop twice in one Itinerary earns one Stamp`() {
        val result = award(completed(stops = listOf(event("a", "e1", 19), event("b", "e1", 21))))
        assertEquals(1, result.created.size)
        assertTrue(result.resultFor("b") is StopStampResult.AlreadyInPassport)
    }

    @Test
    fun `a Stop after midnight is dated by the Itinerary date`() {
        val after = event("a", "e1", 1, day = Nov29)
        val result = award(completed(date = Nov28, stops = listOf(after)))
        assertEquals(Nov28, result.created.single().date)
    }

    @Test
    fun `after midnight Stops are ordered by time, not by list order`() {
        val result = award(completed(stops = listOf(meal("late", "2", 1, day = Nov29), meal("early", "1", 22))))
        assertEquals(listOf("early"), result.created.map { it.stopId })
    }

    @Test
    fun `other people's Stamps are ignored`() {
        val theirs = award(completed(stops = listOf(event("a", "e1", 19)))).created.map { it.copy(ownerUid = "someone-else") }
        val result = award(completed(stops = listOf(event("a", "e1", 19))), theirs)
        assertEquals(1, result.created.size)
    }

    @Test
    fun `two showings of one Event earn one Stamp ever`() {
        val earlier = award(completed(id = "old", stops = listOf(event("a", "show-1", 19)))).created
        val showing = event("b", "show-2", 19, day = Nov29).copy(name = "Event show-1")
        val result = award(completed(id = "new", date = Nov29, stops = listOf(showing)), earlier)
        assertTrue(result.created.isEmpty())
        assertTrue(result.resultFor("b") is StopStampResult.AlreadyInPassport)
    }

    @Test
    fun `Events with one name in different venues are different Stamps`() {
        val far = event("b", "x", 21).copy(name = "Event e1", latitude = 45.6)
        val result = award(completed(stops = listOf(event("a", "e1", 19), far)))
        assertEquals(2, result.created.size)
    }

    @Test
    fun `Event name differences in case, accents and spacing do not matter`() {
        val same = event("b", "x", 21).copy(name = "EVENT  e1")
        assertEquals(1, award(completed(stops = listOf(event("a", "e1", 19), same))).created.size)
    }

    @Test
    fun `chain brands fold the ligature, restaurant prefix and aliases`() {
        assertEquals("loeufrier", ChainBrands.brandOf("L'Œufrier"))
        assertEquals("loeufrier", ChainBrands.brandOf("L'Oeufrier - Laurier"))
        assertEquals("lafleur", ChainBrands.brandOf("Restaurants Lafleur"))
        assertEquals("labelleprovince", ChainBrands.brandOf("Restaurant La Belle Province"))
        assertEquals("bennyco", ChainBrands.brandOf("Benny&Co."))
        assertEquals("bennyco", ChainBrands.brandOf("Benny & Co. (Cavendish)"))
        assertEquals("allomoncoco", ChainBrands.brandOf("Allô! Mon Coco"))
        assertEquals("lacage", ChainBrands.brandOf("La Cage – Brasserie sportive"))
        assertNull(ChainBrands.brandOf("Marius et Fanny"))
        assertNull(ChainBrands.brandOf("Tim Hortons"))
    }

    @Test
    fun `a flagged chain outside the ALLOW brands is its own Place`() {
        val other = place(8, "Tim Hortons", chain = true)
        val r = PlaceStampIdentityResolver({ if (it == "8") other else null })
        assertEquals("place:c8", r.identify(meal("a", "8", 18), "mtl").key)
    }

    @Test
    fun `a Place with is_chain 0 is never merged even with a brand name`() {
        val a = place(8, "Restaurant La Belle Province", chain = false)
        val b = place(9, "La Belle Province", chain = false)
        val r = PlaceStampIdentityResolver({ mapOf("8" to a, "9" to b)[it] })
        val result = awardStamps(completed(stops = listOf(meal("a", "8", 18), meal("b", "9", 20))), emptyList(), r, 1)
        assertEquals(2, result.created.size)
    }

    @Test
    fun `Places are keyed on the stable city id, not the positional id`() {
        val r1 = PlaceStampIdentityResolver({ place(1, "Café Névé", false).copy(cityId = "777") })
        val r2 = PlaceStampIdentityResolver({ place(2, "Café Névé", false).copy(cityId = "777") })
        assertEquals("place:777", r1.identify(meal("a", "1", 18), "mtl").key)
        assertEquals(r1.identify(meal("a", "1", 18), "mtl"), r2.identify(meal("a", "2", 18), "mtl"))
    }

    @Test
    fun `only a Completed Itinerary earns Stamps`() {
        for (phase in ItineraryPhase.entries.filter { it != ItineraryPhase.Completed }) {
            val result = award(completed(phase = phase, stops = listOf(event("a", "e1", 19))))
            assertTrue(result.created.isEmpty())
            assertEquals(StopStampResult.NoStamp, result.resultFor("a"))
        }
    }

    @Test
    fun `a retry answers from the held Stamp even when the Place can no longer be found`() {
        val itinerary = completed(stops = listOf(meal("a", "3", 20)))
        val first = award(itinerary)
        val gone = PlaceStampIdentityResolver({ null })
        val retry = awardStamps(itinerary, first.created, gone, 200)
        assertTrue(retry.created.isEmpty())
        assertEquals(first.outcomes, retry.outcomes)
    }

    private fun place(id: Long, name: String, chain: Boolean, type: VenueType = VenueType.CAFE) = Place(
        id = id, cityId = "c$id", name = name, address = "", location = LatLng(45.5, -73.6), borough = null, venueType = type,
        cuisines = emptyList(), dietary = emptySet(), isChain = chain, price = null,
        description = LocalizedText(null, null), descriptionSource = DescriptionSource.GENERATED,
        rating = null, ratingCount = null, openingHours = null, photoRef = null, googlePlaceId = null,
    )

    @Test
    fun `each new Stamp draws its own shape seed and a retry keeps it`() {
        var next = 0
        val itinerary = completed(stops = listOf(event("a", "e1", 19), meal("b", "3", 21)))
        val first = awardStamps(itinerary, emptyList(), resolver, 100) { next++ }
        assertEquals(listOf(0, 1), first.created.map { it.shapeSeed })
        val retry = awardStamps(itinerary, first.created, resolver, 200) { 99 }
        assertEquals(emptyList<PassportStamp>(), retry.created)
        assertEquals(
            listOf(0, 1),
            listOf("a", "b").map { ((retry.resultFor(it) as StopStampResult.NewStamp).stamp.shapeSeed) },
        )
    }
}
