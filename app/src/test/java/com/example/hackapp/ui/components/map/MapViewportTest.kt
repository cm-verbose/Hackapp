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
        val start = MapViewport(zoom = 2f, panX = -40f, panY = -90f, rotation = 33f)
        val fittedX = start.fittedX(150f, 320f)
        val fittedY = start.fittedY(150f, 320f)
        val zoomed = start.zoomedTo(5f, 150f, 320f)
        assertEquals(150f, zoomed.screenX(fittedX, fittedY), 0.01f)
        assertEquals(320f, zoomed.screenY(fittedX, fittedY), 0.01f)
    }

    @Test
    fun rotationKeepsTheFocalPointInPlace() {
        val start = MapViewport(zoom = 2f, panX = -40f, panY = -90f, rotation = 10f)
        val fittedX = start.fittedX(150f, 320f)
        val fittedY = start.fittedY(150f, 320f)
        val turned = start.rotatedBy(47f, 150f, 320f)
        assertEquals(57f, turned.rotation, 0.001f)
        assertEquals(150f, turned.screenX(fittedX, fittedY), 0.01f)
        assertEquals(320f, turned.screenY(fittedX, fittedY), 0.01f)
    }

    @Test
    fun rotationStaysInTheShortestRange() {
        assertEquals(-170f, normalizedDegrees(190f), 0.001f)
        assertEquals(170f, normalizedDegrees(-190f), 0.001f)
        assertEquals(180f, normalizedDegrees(-180f), 0.001f)
    }

    @Test
    fun rotatedContentIsClampedByItsTurnedExtents() {
        val clamped = MapViewport(panX = 900f, panY = 900f, rotation = 90f).clamped(content, visible)
        val xs = listOf(clamped.screenX(content.left, content.top), clamped.screenX(content.right, content.bottom))
        val ys = listOf(clamped.screenY(content.left, content.top), clamped.screenY(content.right, content.bottom))
        assertEquals(200f, (xs[0] + xs[1]) / 2f, 0.01f)
        assertEquals(400f, (ys[0] + ys[1]) / 2f, 0.01f)
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
        assertEquals(300f, clamped.screenY(0f, (content.top + content.bottom) / 2f), 0.001f)
    }

    @Test
    fun zoomedContentCannotLeaveTheVisibleArea() {
        val far = MapViewport(zoom = 6f, panX = 9000f, panY = 9000f).clamped(content, visible)
        assertEquals(visible.left, far.screenX(content.left, content.top), 0.001f)
        assertEquals(visible.top, far.screenY(content.left, content.top), 0.001f)

        val other = MapViewport(zoom = 6f, panX = -9000f, panY = -9000f).clamped(content, visible)
        assertEquals(visible.right, other.screenX(content.right, content.bottom), 0.001f)
        assertEquals(visible.bottom, other.screenY(content.right, content.bottom), 0.001f)
    }

    @Test
    fun tapTransformRoundTripsAtEveryZoomAndRotation() {
        listOf(1f, 1.5f, 2f, 3.7f, 6f).forEach { zoom ->
            listOf(0f, 45f, 90f, -135f).forEach { rotation ->
                val viewport = MapViewport(zoom, panX = -123f, panY = 45f, rotation = rotation)
                val x = viewport.screenX(210f, 333f)
                val y = viewport.screenY(210f, 333f)
                assertEquals(210f, viewport.fittedX(x, y), 0.01f)
                assertEquals(333f, viewport.fittedY(x, y), 0.01f)
            }
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
