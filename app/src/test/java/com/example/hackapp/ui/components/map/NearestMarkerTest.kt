package com.example.hackapp.ui.components.map

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NearestMarkerTest {
    private val markers = listOf(Offset(100f, 100f), Offset(120f, 100f))

    @Test
    fun overlappingTouchAreasPickTheClosestMarker() {
        assertEquals(markers[0], nearestWithin(Offset(105f, 100f), markers, 24f) { it })
        assertEquals(markers[1], nearestWithin(Offset(115f, 100f), markers, 24f) { it })
    }

    @Test
    fun aTapOutsideEveryTouchAreaPicksNothing() {
        assertNull(nearestWithin(Offset(200f, 200f), markers, 24f) { it })
    }
}
