package com.example.hackapp.itinerary

import com.example.hackapp.data.model.CuisineGroup
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.data.model.LocalizedText
import com.example.hackapp.data.model.Place
import com.example.hackapp.data.model.PriceRange
import com.example.hackapp.data.model.VenueType
import com.example.hackapp.profile.AudiencePreference
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.services.LegEstimate
import com.example.hackapp.services.LegEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZonedDateTime

class MealPlanningTest {
    private val legs = fixedLegs(20)
    private val dinner = MealBlock("d", MealKind.Dinner, LocalTime.of(19, 0), budget = BudgetBracket.Under20)

    private fun search(
        places: List<Place>,
        block: MealBlock = dinner,
        itinerary: Itinerary = night(blocks = listOf(block)),
        context: PlanningContext = context(),
        next: String? = NEXT,
        catalog: List<CuisineGroup> = emptyList(),
        isOpenAt: (Place, ZonedDateTime) -> Boolean = { _, _ -> true },
    ) = MealSearch(itinerary, block, places, context, HERE, next, cuisineCatalog = catalog, isOpenAt = isOpenAt)

    private fun hereIds(s: MealSearch) = mealOptions(s, legs).here.map { it.place.id }

    @Test
    fun `default blocks are only those fully inside the window`() {
        fun kinds(itinerary: Itinerary) = defaultMealBlocks(itinerary, context()).map { it.kind }
        assertEquals(listOf(MealKind.Lunch), kinds(day()))
        assertEquals(listOf(MealKind.Dinner, MealKind.LateBite), kinds(night()))
        assertEquals(emptyList<MealKind>(), kinds(night(start = LocalTime.of(11, 0), end = LocalTime.of(13, 0))))
        assertEquals(
            listOf(MealKind.Lunch),
            kinds(night(start = LocalTime.of(12, 0), end = LocalTime.of(13, 30))),
        )
    }

    @Test
    fun `block lengths and default times match the decisions`() {
        assertEquals(90, MealKind.Lunch.blockMinutes)
        assertEquals(90, MealKind.Dinner.blockMinutes)
        assertEquals(45, MealKind.LateBite.blockMinutes)
        assertEquals(LocalTime.of(12, 0), MealKind.Lunch.defaultStart)
        assertEquals(LocalTime.of(18, 30), MealKind.Dinner.defaultStart)
        assertEquals(LocalTime.of(22, 0), MealKind.LateBite.defaultStart)
    }

    @Test
    fun `default blocks take the Meal budgets`() {
        val ctx = context().copy(defaultMealBudgets = mapOf(MealKind.Dinner to BudgetBracket.Under50))
        val block = defaultMealBlocks(night(), ctx).first { it.kind == MealKind.Dinner }
        assertEquals(BudgetBracket.Under50, block.budget)
    }

    @Test
    fun `budget uses the top of the range and keeps unknown prices`() {
        val places = listOf(
            place(1, price = PriceRange(10, 20, false)),
            place(2, price = PriceRange(10, 21, false)),
            place(3, price = null),
            place(4, price = PriceRange(100, null, false)),
        )
        assertEquals(listOf(1L, 3L), hereIds(search(places)))
    }

    @Test
    fun `the no-limit bracket keeps every price`() {
        val block = dinner.copy(budget = BudgetBracket.From100)
        val places = listOf(place(1, price = PriceRange(100, null, false)))
        assertEquals(listOf(1L), hereIds(search(places, block = block)))
    }

    @Test
    fun `unknown prices rank below known prices`() {
        val places = listOf(
            place(1, price = null, location = LatLng(45.5, -73.6)),
            place(2, location = LatLng(45.6, -73.6)),
        )
        val itinerary = night(stops = listOf(stop("p", at(Nov28, 18), at(Nov28, 18, 30))), blocks = listOf(dinner))
        assertEquals(listOf(2L, 1L), hereIds(search(places, itinerary = itinerary)))
    }

    @Test
    fun `sorted by distance from the previous Stop with a cuisine boost`() {
        val itinerary = night(stops = listOf(stop("p", at(Nov28, 18), at(Nov28, 18, 30))), blocks = listOf(dinner))
        val places = listOf(
            place(1, location = LatLng(45.51, -73.6)),
            place(2, location = LatLng(45.50, -73.6)),
            place(3, location = LatLng(45.52, -73.6), cuisines = listOf("thai")),
        )
        assertEquals(listOf(2L, 1L, 3L), hereIds(search(places, itinerary = itinerary)))

        val group = CuisineGroup("asian", "ramen_dining", LocalizedText(null, null), setOf("thai"), null)
        val ctx = context().copy(cuisineGroups = setOf("asian"))
        assertEquals(
            listOf(3L, 2L, 1L),
            hereIds(search(places, itinerary = itinerary, context = ctx, catalog = listOf(group))),
        )
    }

    @Test
    fun `must be open for the whole block`() {
        val closesEarly = place(1)
        val open = place(2)
        val lastMinute = at(Nov28, 20, 29).toInstant()
        val found = hereIds(
            search(listOf(closesEarly, open)) { p, time ->
                p.id != 1L || time.toInstant().isBefore(lastMinute)
            },
        )
        assertEquals(listOf(2L), found)
    }

    @Test
    fun `closing right at the block end still fits`() {
        val blockEnd = at(Nov28, 20, 30).toInstant()
        val found = hereIds(search(listOf(place(1))) { _, time -> time.toInstant().isBefore(blockEnd) })
        assertEquals(listOf(1L), found)
    }

    @Test
    fun `bars start at 17_00`() {
        val bar = place(1, venueType = VenueType.BAR)
        val at1659 = MealBlock("a", MealKind.Lunch, LocalTime.of(16, 59), BudgetBracket.Under20)
        val at1700 = MealBlock("b", MealKind.Lunch, LocalTime.of(17, 0), BudgetBracket.Under20)
        val window = day()
        assertTrue(hereIds(search(listOf(bar), block = at1659, itinerary = window)).isEmpty())
        assertEquals(listOf(1L), hereIds(search(listOf(bar), block = at1700, itinerary = window)))
    }

    @Test
    fun `bars remain available after midnight`() {
        val bar = place(1, venueType = VenueType.BAR)
        val late = MealBlock("l", MealKind.LateBite, LocalTime.of(0, 30), BudgetBracket.Under20)
        assertEquals(listOf(1L), hereIds(search(listOf(bar), block = late)))
    }

    @Test
    fun `bars are hidden for Family-friendly and No alcohol`() {
        val bar = place(1, venueType = VenueType.BAR)
        listOf(AudiencePreference.FamilyFriendly, AudiencePreference.NoAlcohol).forEach { preference ->
            val ctx = context().copy(audience = setOf(preference))
            assertTrue(hereIds(search(listOf(bar), context = ctx)).isEmpty())
        }
    }

    @Test
    fun `next District requires arriving by the block start`() {
        val prior = stop("p", at(Nov28, 18), at(Nov28, 18, 40))
        val places = listOf(place(1, district = NEXT))
        val onTime = night(stops = listOf(prior), blocks = listOf(dinner))
        assertEquals(1, mealOptions(search(places, itinerary = onTime), legs).nextDistrict.size)

        val late = night(
            stops = listOf(prior.copy(plannedDeparture = at(Nov28, 18, 41))),
            blocks = listOf(dinner),
        )
        assertTrue(mealOptions(search(places, itinerary = late), legs).nextDistrict.isEmpty())
    }

    @Test
    fun `no next District leaves the second tab empty`() {
        val options = mealOptions(search(listOf(place(1, district = NEXT)), next = null), legs)
        assertTrue(options.nextDistrict.isEmpty())
    }

    @Test
    fun `next District options use transit`() {
        var used: com.example.hackapp.services.LegMode? = null
        val estimator = LegEstimator { _, _, mode ->
            used = mode
            LegEstimate(1.0, 1, 1)
        }
        val prior = stop("p", at(Nov28, 18), at(Nov28, 18, 30))
        mealOptions(
            search(listOf(place(1, district = NEXT)), itinerary = night(stops = listOf(prior), blocks = listOf(dinner))),
            estimator,
        )
        assertEquals(com.example.hackapp.services.LegMode.TRANSIT, used)
    }

    @Test
    fun `an after-midnight block sits on the next calendar day`() {
        val late = MealBlock("l", MealKind.LateBite, LocalTime.of(0, 30))
        val interval = late.interval(night(), Toronto)
        assertEquals(instant(Nov28.plusDays(1), 0, 30), interval.start)
        assertEquals(instant(Nov28.plusDays(1), 1, 15), interval.end)
    }
}
