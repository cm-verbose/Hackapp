package com.example.hackapp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class StampInitialsTest {
    @Test
    fun `uses the first two words`() {
        assertEquals("JS", stampInitials("Jazz sur le toit"))
        assertEquals("MJ", stampInitials("Marché Jean-Talon"))
        assertEquals("BG", stampInitials("Bar & Grill"))
    }

    @Test
    fun `splits after a one-letter elision`() {
        assertEquals("LA", stampInitials("L'Avenue"))
        assertEquals("LA", stampInitials("L’Avenue du Plateau"))
        assertEquals("DA", stampInitials("D'Arcy McGee"))
    }

    @Test
    fun `keeps a possessive as one word`() {
        assertEquals("S", stampInitials("Schwartz's"))
        assertEquals("SD", stampInitials("Schwartz's Deli"))
        assertEquals("MP", stampInitials("Moe’s Pub"))
    }

    @Test
    fun `ignores leading punctuation`() {
        assertEquals("J", stampInitials(":JOA"))
        assertEquals("LB", stampInitials("(Le) Bistro"))
    }

    @Test
    fun `keeps accents and digits`() {
        assertEquals("ÉD", stampInitials("Éclair Doré"))
        assertEquals("3B", stampInitials("3 Brasseurs"))
    }

    @Test
    fun `wide initials are capped to a round shape at large text`() {
        // "MM" at 1.3x in the 100 dp Cookie7: the wanted 1.75 would overflow its safe circle.
        val scale = fittedInitialsScale(1.75f, textWidth = 60f, textHeight = 40f, boxWidth = 100f, boxHeight = 100f, round = true)
        assertTrue(scale < 1.75f)
        assertTrue(hypot(60f * scale, 40f * scale) <= 76.01f)
    }

    @Test
    fun `a rounded rectangle is limited by its height`() {
        val scale = fittedInitialsScale(1.75f, textWidth = 40f, textHeight = 50f, boxWidth = 160f, boxHeight = 80f, round = false)
        assertEquals(80f * 0.82f / 50f, scale, 0.001f)
    }

    @Test
    fun `short initials keep the wanted scale`() {
        assertEquals(1.75f, fittedInitialsScale(1.75f, 20f, 24f, 110f, 110f, round = true), 0f)
        assertEquals(1.5f, fittedInitialsScale(1.5f, 0f, 0f, 0f, 0f, round = true), 0f)
    }

    @Test
    fun `is never blank`() {
        listOf("", "   ", "!!!", "-", "'", "🍕").forEach {
            assertTrue("blank for '$it'", stampInitials(it).isNotBlank())
        }
    }
}
