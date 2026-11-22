package com.example.hackapp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `is never blank`() {
        listOf("", "   ", "!!!", "-", "'", "🍕").forEach {
            assertTrue("blank for '$it'", stampInitials(it).isNotBlank())
        }
    }
}
