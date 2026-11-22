package com.example.hackapp.ui.components

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {
    private val fr = Locale.CANADA_FRENCH
    private val en = Locale.CANADA

    @Test
    fun amountsDropWholeDecimalsAndFollowTheLocale() {
        assertEquals("25 $", formatCad(25.0, fr))
        assertEquals("12,50 $", formatCad(12.5, fr))
        assertEquals("$25", formatCad(25, en))
        assertEquals("$12.50", formatCad(12.5, en))
    }

    @Test
    fun rangesUseAnEnDashAndOneSymbol() {
        assertEquals("10–20 $", formatCadRange(10, 20, fr))
        assertEquals("$10–20", formatCadRange(10, 20, en))
        assertEquals("20 $", formatCadRange(20, 20, fr))
    }

    @Test
    fun openBracketMatchesTheBudgetLabel() {
        assertEquals("100 $ +", formatCadRange(100, null, fr))
        assertEquals("$100+", formatCadRange(100, null, en))
    }

    @Test
    fun durationsUseHoursAndMinutes() {
        assertEquals("2 h", formatDurationMinutes(120, fr))
        assertEquals("45 min", formatDurationMinutes(45, en))
        assertEquals("1 h 05", formatDurationMinutes(65, fr))
        assertEquals("1 h 30 min", formatDurationMinutes(90, en))
    }
}
