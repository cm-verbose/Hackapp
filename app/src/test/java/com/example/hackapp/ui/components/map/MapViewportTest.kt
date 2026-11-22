package com.example.hackapp.ui.components.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewportTest {
    private val visible = MapBounds(0f, 100f, 400f, 700f)

    /** A wide map: narrower than the canvas height, as on a phone in portrait. */
    private val content = MapBounds(0f, 250f, 400f, 550f)

    @Test
    fun zoomIsLimitedToOneToSix() {
        assertEquals(6f, MapViewport().zoomedTo(50f, 10f, 10f).zoom)
        assertEquals(1f, MapViewport(zoom = 3f).zoomedTo(0.1f, 10f, 10f).zoom)
    }

    @Test
    fun focalPointStaysUnderTheFingers() {
        val start = MapViewport(zoom = 2f, panX = -40f, panY = -90f)
        val fittedX = start.fittedX(150f)
        val fittedY = start.fittedY(320f)
        val zoomed = start.zoomedTo(5f, 150f, 320f)
        assertEquals(150f, zoomed.screenX(fittedX), 0.001f)
        assertEquals(320f, zoomed.screenY(fittedY), 0.001f)
    }

    @Test
    fun contentThatFitsIsCentredInTheVisibleArea() {
        val clamped = MapViewport(panX = 80f, panY = 500f).clamped(content, visible)
        assertEquals(0f, clamped.panX, 0.001f)
        assertEquals(0f, clamped.panY, 0.001f)
    }

    @Test
    fun visibleAreaBelowAnOverlayMovesTheCentre() {
        val clamped = MapViewport().clamped(content, MapBounds(0f, 100f, 400f, 500f))
        assertEquals(300f, clamped.screenY((content.top + content.bottom) / 2f), 0.001f)
    }

    @Test
    fun zoomedContentCannotLeaveTheVisibleArea() {
        val far = MapViewport(zoom = 6f, panX = 9000f, panY = 9000f).clamped(content, visible)
        assertEquals(visible.left, far.screenX(content.left), 0.001f)
        assertEquals(visible.top, far.screenY(content.top), 0.001f)

        val other = MapViewport(zoom = 6f, panX = -9000f, panY = -9000f).clamped(content, visible)
        assertEquals(visible.right, other.screenX(content.right), 0.001f)
        assertEquals(visible.bottom, other.screenY(content.bottom), 0.001f)
    }

    @Test
    fun tapTransformRoundTripsAtEveryZoom() {
        listOf(1f, 1.5f, 2f, 3.7f, 6f).forEach { zoom ->
            val viewport = MapViewport(zoom, panX = -123f, panY = 45f)
            assertEquals(210f, viewport.fittedX(viewport.screenX(210f)), 0.001f)
            assertEquals(333f, viewport.fittedY(viewport.screenY(333f)), 0.001f)
        }
    }

    @Test
    fun doubleTapStepsThroughZoomLevels() {
        assertEquals(2f, nextDoubleTapZoom(1f))
        assertEquals(4f, nextDoubleTapZoom(2f))
        assertEquals(6f, nextDoubleTapZoom(4f))
        assertEquals(1f, nextDoubleTapZoom(6f))
    }

    @Test
    fun higherCountWinsAnOverlap() {
        val badges = listOf(badge("low", 100f, 2), badge("high", 110f, 9))
        assertEquals(setOf("high"), visibleBadgeIds(badges, zoom = 1f, diameter = 24f))
    }

    @Test
    fun zoomingInRevealsHiddenBadges() {
        val badges = listOf(badge("low", 100f, 2), badge("high", 110f, 9))
        assertEquals(setOf("high", "low"), visibleBadgeIds(badges, zoom = 3f, diameter = 24f))
    }

    @Test
    fun selectedBadgesAlwaysShow() {
        val badges = listOf(
            badge("a", 100f, 1, selected = true),
            badge("b", 101f, 1, selected = true),
            badge("busy", 102f, 50),
        )
        assertEquals(setOf("a", "b"), visibleBadgeIds(badges, zoom = 1f, diameter = 24f))
    }

    @Test
    fun equalCountsResolveTheSameWayEveryTime() {
        val one = listOf(badge("b", 100f, 3), badge("a", 105f, 3))
        assertEquals(visibleBadgeIds(one, 1f, 24f), visibleBadgeIds(one.reversed(), 1f, 24f))
        assertTrue("a" in visibleBadgeIds(one, 1f, 24f))
    }

    @Test
    fun badgeTapUsesScreenPositionAtZoom() {
        val badges = listOf(badge("a", 100f, 3))
        val viewport = MapViewport(zoom = 4f, panX = -200f, panY = 0f)
        assertEquals("a", badgeAt(badges, setOf("a"), viewport, 200f, 400f, 12f))
        assertNull(badgeAt(badges, setOf("a"), viewport, 100f, 100f, 12f))
        assertNull(badgeAt(badges, emptySet(), viewport, 200f, 400f, 12f))
    }

    private fun badge(id: String, x: Float, count: Int, selected: Boolean = false) =
        MapBadge(id, x, 100f, count, selected)
}
