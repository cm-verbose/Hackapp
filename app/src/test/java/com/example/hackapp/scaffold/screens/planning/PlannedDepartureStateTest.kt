package com.example.hackapp.scaffold.screens.planning

import com.example.hackapp.itinerary.Nov28
import com.example.hackapp.itinerary.Toronto
import com.example.hackapp.itinerary.at
import com.example.hackapp.itinerary.day
import com.example.hackapp.itinerary.event
import com.example.hackapp.itinerary.night
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class PlannedDepartureStateTest {
    private fun holder(startHour: Int, startMinute: Int = 0, type: String = "music", end: String? = null, onNov29: Boolean = false) =
        PlannedDepartureHolder.create(
            event("e", start = at(if (onNov29) Nov28.plusDays(1) else Nov28, startHour, startMinute).toString(), type = type, end = end),
            night(),
            Toronto,
        )!!

    @Test
    fun `prefills the typical stay for the type`() {
        val s = holder(19, type = "theatre").state.value
        assertEquals(150, s.stayMinutes)
        assertEquals(at(Nov28, 21, 30).toInstant(), s.departure.toInstant())
        assertTrue(s.isTypicalStay)
        assertFalse(s.hasPublishedEnd)
        assertFalse(s.crossesMidnight)
    }

    @Test
    fun `unknown type uses the default and a published end is kept`() {
        assertEquals(90, holder(19, type = "weird").state.value.stayMinutes)
        val published = holder(19, end = at(Nov28, 20, 15).toString()).state.value
        assertTrue(published.hasPublishedEnd)
        assertEquals(75, published.stayMinutes)
        assertFalse(published.isTypicalStay)
    }

    @Test
    fun `no start gives no holder`() {
        assertNull(PlannedDepartureHolder.create(event("x", start = null), night(), Toronto))
    }

    @Test
    fun `prefill is clamped to the window end`() {
        val s = holder(1, onNov29 = true).state.value
        assertEquals(at(Nov28.plusDays(1), 2).toInstant(), s.departure.toInstant())
        assertEquals(60, s.stayMinutes)
        assertTrue(s.canConfirm)
    }

    @Test
    fun `typical stay can cross midnight`() {
        val s = holder(23, 30).state.value
        assertTrue(s.crossesMidnight)
        assertEquals(Nov28.plusDays(1), s.departure.toLocalDate())
    }

    @Test
    fun `a time after midnight rolls to the next day`() {
        val h = holder(23, 30)
        h.setTime(LocalTime.of(1, 0))
        val s = h.state.value
        assertEquals(90, s.stayMinutes)
        assertEquals(Nov28.plusDays(1), s.departure.toLocalDate())
        assertTrue(s.crossesMidnight)
        assertFalse(s.isTypicalStay)
        assertTrue(s.canConfirm)
    }

    @Test
    fun `a time at or before the start rolls to tomorrow and may fall outside the window`() {
        val h = holder(21)
        h.setTime(LocalTime.of(21, 0))
        assertEquals(DepartureError.OutsideWindow, h.state.value.error)
        assertEquals(LocalTime.of(21, 0), h.state.value.rejectedTime)
        assertNull(h.confirm())
    }

    @Test
    fun `a time after the window end is rejected and keeps the last choice`() {
        val h = holder(21)
        val before = h.state.value.departure
        h.setTime(LocalTime.of(3, 0))
        assertEquals(DepartureError.OutsideWindow, h.state.value.error)
        assertEquals(before, h.state.value.departure)
        h.setTime(LocalTime.of(23, 0))
        assertNull(h.state.value.error)
        assertEquals(at(Nov28, 23).toInstant(), h.confirm()!!.toInstant())
    }

    @Test
    fun `the window end itself is allowed`() {
        val h = holder(21)
        h.setTime(LocalTime.of(2, 0))
        assertNull(h.state.value.error)
        assertEquals(300, h.state.value.stayMinutes)
    }

    @Test
    fun `stay minutes must be positive and inside the window`() {
        val h = holder(21)
        h.setStayMinutes(0)
        assertEquals(DepartureError.NotAfterStart, h.state.value.error)
        h.setStayMinutes(600)
        assertEquals(DepartureError.OutsideWindow, h.state.value.error)
        h.setStayMinutes(45)
        assertNull(h.state.value.error)
        assertEquals(45, h.state.value.stayMinutes)
        h.reset()
        assertTrue(h.state.value.isTypicalStay)
        assertEquals(120, h.state.value.stayMinutes)
    }

    @Test
    fun `day Itinerary window works the same`() {
        val h = PlannedDepartureHolder.create(event("e", start = at(Nov28, 17).toString(), type = "sport"), day(), Toronto)!!
        assertEquals(60, h.state.value.stayMinutes)
        assertEquals(at(Nov28, 18).toInstant(), h.state.value.departure.toInstant())
    }
}
