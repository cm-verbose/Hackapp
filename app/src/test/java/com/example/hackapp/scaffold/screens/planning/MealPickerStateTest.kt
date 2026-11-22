package com.example.hackapp.scaffold.screens.planning

import com.example.hackapp.data.model.PriceRange
import com.example.hackapp.itinerary.MealBlock
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.itinerary.MealSearch
import com.example.hackapp.itinerary.activeMealBlocks
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

    @Test
    fun `distance and walking time come from the previous Stop`() {
        val withStop = night(stops = listOf(stop("p", at(Nov28, 18), at(Nov28, 18, 30))), blocks = listOf(block))
        val row = holder(listOf(place(1)), it = withStop).state.value.here.single()
        assertNotNull(row.distanceMeters)
        assertEquals(10, row.walkingMinutes)
    }

    @Test
    fun `no reference point gives no distance`() {
        val row = holder(listOf(place(1))).state.value.here.single()
        assertNull(row.distanceMeters)
        assertNull(row.walkingMinutes)
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
}

