package com.example.hackapp.scaffold.screens.planning

import com.example.hackapp.data.model.LatLng
import com.example.hackapp.data.model.PriceRange
import com.example.hackapp.itinerary.MealBlock
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.itinerary.MealSearch
import com.example.hackapp.itinerary.activeMealBlocks
import com.example.hackapp.itinerary.withStop
import com.example.hackapp.itinerary.HERE
import com.example.hackapp.itinerary.NEXT
import com.example.hackapp.itinerary.Nov28
import com.example.hackapp.itinerary.at
import com.example.hackapp.itinerary.context
import com.example.hackapp.itinerary.fixedLegs
import com.example.hackapp.itinerary.night
import com.example.hackapp.itinerary.place
import com.example.hackapp.itinerary.stop
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.services.LegEstimate
import com.example.hackapp.services.LegEstimator
import com.example.hackapp.services.LegMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class MealPickerStateTest {
    private val block = MealBlock("d", MealKind.Dinner, LocalTime.of(19, 0), budget = BudgetBracket.Under20)
    private val itinerary = night(blocks = listOf(block))

    private fun holder(places: List<com.example.hackapp.data.model.Place>, next: String? = NEXT, it: com.example.hackapp.itinerary.Itinerary = itinerary) =
        MealPickerHolder(MealSearch(it, block, places, context(), HERE, next), fixedLegs(20))

    @Test
    fun `second tab is omitted without a next District`() {
        val state = holder(listOf(place(1)), next = null).state.value
        assertEquals(listOf(MealTab.Here), state.tabs)
        assertNull(state.nextDistrictId)
        val h = holder(listOf(place(1)), next = null)
        h.selectTab(MealTab.NextDistrict)
        assertEquals(MealTab.Here, h.state.value.selectedTab)
    }

    @Test
    fun `both tabs are offered and switch`() {
        val h = holder(listOf(place(1), place(2, district = NEXT)))
        assertEquals(listOf(MealTab.Here, MealTab.NextDistrict), h.state.value.tabs)
        assertEquals(listOf(1L), h.state.value.options.map { it.placeId })
        h.selectTab(MealTab.NextDistrict)
        assertEquals(listOf(2L), h.state.value.options.map { it.placeId })
        assertTrue(h.state.value.options.single().inNextDistrict)
    }

    @Test
    fun `rows show ranges with the counted top and unknown prices rank last`() {
        val h = holder(listOf(place(1, price = null), place(2, price = PriceRange(10, 20, false))))
        val rows = h.state.value.here
        assertEquals(listOf(2L, 1L), rows.map { it.placeId })
        val range = rows[0].price as MealPrice.Range
        assertEquals("$10–20", range.range.label())
        assertEquals(20, range.countedCad)
        assertEquals(MealPrice.Unknown, rows[1].price)
    }

    @Test
    fun `over budget places are hidden`() {
        val h = holder(listOf(place(1, price = PriceRange(10, 30, false))))
        assertTrue(h.state.value.isEmpty)
        assertTrue(h.state.value.hasNoOptions)
    }

    /** Walking takes 7 minutes and transit 13, plus a 10-minute buffer that is never shown. */
    private val modeLegs = LegEstimator { _, _, mode ->
        LegEstimate(1000.0, if (mode == LegMode.WALK) 7 else 13, 10)
    }

    private fun rows(
        places: List<com.example.hackapp.data.model.Place>,
        previousDistrict: String? = HERE,
        origin: LatLng? = null,
    ): Pair<List<MealOptionRow>, List<MealOptionRow>> {
        val stops = previousDistrict?.let { listOf(stop("p", at(Nov28, 18), at(Nov28, 18, 30), district = it)) }.orEmpty()
        val plan = night(stops = stops, blocks = listOf(block))
        return mealPickerRows(MealSearch(plan, block, places, context(), HERE, NEXT, origin = origin), modeLegs)
    }

    @Test
    fun `a Meal in the District of the previous Stop shows walking time`() {
        val row = rows(listOf(place(1))).first.single()
        assertNotNull(row.distanceMeters)
        assertEquals(MealTravel(LegMode.WALK, 7), row.travel)
    }

    @Test
    fun `a Meal in the next District shows transit time without the buffer`() {
        val row = rows(listOf(place(1, district = NEXT))).second.single()
        assertEquals(MealTravel(LegMode.TRANSIT, 13), row.travel)
    }

    @Test
    fun `the mode follows the previous Stop and not the selected tab`() {
        val (here, next) = rows(listOf(place(1), place(2, district = NEXT)), previousDistrict = NEXT)
        assertEquals(MealTravel(LegMode.TRANSIT, 13), here.single().travel)
        assertEquals(MealTravel(LegMode.WALK, 7), next.single().travel)
    }

    @Test
    fun `without a previous Stop the current District is the reference`() {
        val (here, next) = rows(
            listOf(place(1), place(2, district = NEXT)),
            previousDistrict = null,
            origin = LatLng(45.5, -73.6),
        )
        assertEquals(LegMode.WALK, here.single().travel?.mode)
        assertEquals(LegMode.TRANSIT, next.single().travel?.mode)
    }

    @Test
    fun `no reference point gives no distance`() {
        val row = holder(listOf(place(1))).state.value.here.single()
        assertNull(row.distanceMeters)
        assertNull(row.travel)
    }

    @Test
    fun `choosing in the next District moves planning there`() {
        val h = holder(listOf(place(1), place(2, district = NEXT)))
        val here = h.choose(1)!!
        assertNull(here.moveToDistrictId)
        assertEquals("1", here.stop.sourceId)
        assertEquals(NEXT, h.choose(2)!!.moveToDistrictId)
        assertNull(h.choose(99))
    }

    @Test
    fun `skipping frees the block`() {
        val outcome = holder(listOf(place(1))).skip()
        assertEquals("d", outcome.blockId)
        val skipped = itinerary.withMealSkipped(outcome.blockId)
        assertTrue(skipped.mealBlocks.single().skipped)
        assertTrue(skipped.activeMealBlocks.isEmpty())
    }

    @Test
    fun `choosing again replaces the Stop of the same block and skipping removes it`() {
        val h = holder(listOf(place(1), place(2)))
        val first = h.choose(1)!!.stop
        val second = h.choose(2)!!.stop
        val it2 = itinerary.withStop(first).withStop(second)
        assertEquals(listOf(second.id), it2.stops.map { it.id })
        assertTrue(it2.withMealSkipped("d").stops.isEmpty())
    }
}

