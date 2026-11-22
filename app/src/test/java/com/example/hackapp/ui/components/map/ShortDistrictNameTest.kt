package com.example.hackapp.ui.components.map

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortDistrictNameTest {
    @Test
    fun `the City Pack short name wins`() {
        assertEquals("CDN", shortDistrictName("Côte-des-Neiges–Notre-Dame-de-Grâce", "CDN"))
    }

    @Test
    fun `without one, the first letters of the main words, at most three`() {
        assertEquals("VM", shortDistrictName("Ville-Marie", null))
        assertEquals("PMR", shortDistrictName("Le Plateau-Mont-Royal", null))
        assertEquals("ÎBS", shortDistrictName("L’Île-Bizard–Sainte-Geneviève", null))
        assertEquals("ANJ", shortDistrictName("Anjou", null))
    }
}
