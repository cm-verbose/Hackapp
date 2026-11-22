package com.example.hackapp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeWheelMathTest {
    @Test
    fun wheelPageShowsTheValue() {
        listOf(0, 7, 23).forEach { value ->
            assertEquals(value, Math.floorMod(wheelPageFor(value, 24, 24_000), 24))
        }
    }

    @Test
    fun wheelTakesTheShortWayAcrossMidnight() {
        // Page 24 000 shows 00, so 23 is one page back.
        assertEquals(23_999, wheelPageFor(23, 24, 24_000))
    }

    @Test
    fun typedValuesMustFitTheWheel() {
        assertEquals(9, parseTimeUnit("09", 24))
        assertEquals(59, parseTimeUnit("59", 60))
        assertNull(parseTimeUnit("24", 24))
        assertNull(parseTimeUnit("", 60))
    }

    @Test
    fun valuesShowTwoDigits() {
        assertEquals("05", twoDigits(5))
        assertEquals("18", twoDigits(18))
    }
}
