package com.example.hackapp.ui.components

import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceTimeTest {
    @Test
    fun frenchUsesHWithNonBreakingSpaces() {
        assertEquals("19 h 15", formatSentenceTime(LocalTime.of(19, 15), Locale.CANADA_FRENCH))
        assertEquals("9 h 05", formatSentenceTime(LocalTime.of(9, 5), Locale.FRENCH))
    }

    @Test
    fun englishUsesColon() {
        assertEquals("19:15", formatSentenceTime(LocalTime.of(19, 15), Locale.CANADA))
        assertEquals("09:05", formatSentenceTime(LocalTime.of(9, 5), Locale.ENGLISH))
    }
}
