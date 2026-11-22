package com.example.hackapp.scaffold.sandbox.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ThrowGeometryTest {

    private val eps = 0.5f

    /** Phone lying flat, screen up, top of the screen pointing at [headingDeg]. */
    private fun flat(headingDeg: Float): FloatArray {
        val h = Math.toRadians(headingDeg.toDouble())
        val c = cos(h).toFloat()
        val s = sin(h).toFloat()
        // Columns: device x (right), device y (top), device z (out of screen) in E,N,U.
        return floatArrayOf(
            c, s, 0f,
            -s, c, 0f,
            0f, 0f, 1f,
        )
    }

    /** Phone held upright in front of the user, back of the phone facing [headingDeg]. */
    private fun upright(headingDeg: Float): FloatArray {
        val h = Math.toRadians(headingDeg.toDouble())
        val c = cos(h).toFloat()
        val s = sin(h).toFloat()
        // x = right = (cos, -sin, 0), y = up, z = towards the user = -(sin, cos, 0).
        return floatArrayOf(
            c, 0f, -s,
            -s, 0f, -c,
            0f, 1f, 0f,
        )
    }

    private fun assertBearing(expected: Float, actual: Float?) {
        assertNotNull(actual)
        assertEquals(0f, ThrowGeometry.angleDiff(expected, actual!!), eps)
    }

    @Test
    fun forwardBearing_flatAndUpright() {
        assertBearing(0f, ThrowGeometry.forwardBearing(flat(0f)))
        assertBearing(90f, ThrowGeometry.forwardBearing(flat(90f)))
        assertBearing(0f, ThrowGeometry.forwardBearing(upright(0f)))
        assertBearing(225f, ThrowGeometry.forwardBearing(upright(225f)))
    }

    @Test
    fun screenToBearing_swipeDirections() {
        val r = flat(0f)
        assertBearing(0f, ThrowGeometry.screenToBearing(r, 0f, -1f)) // up the screen
        assertBearing(90f, ThrowGeometry.screenToBearing(r, 1f, 0f)) // right
        assertBearing(180f, ThrowGeometry.screenToBearing(r, 0f, 1f)) // down
        assertBearing(270f, ThrowGeometry.screenToBearing(r, -1f, 0f)) // left
        assertBearing(90f, ThrowGeometry.screenToBearing(upright(90f), 0f, -1f))
    }

    @Test
    fun bearingToScreen_isInverseOfScreenToBearing() {
        val r = flat(37f)
        val bearing = ThrowGeometry.screenToBearing(r, 0.6f, -0.8f)!!
        val (sx, sy) = ThrowGeometry.bearingToScreen(r, bearing)!!
        assertEquals(0.6f, sx, 0.01f)
        assertEquals(-0.8f, sy, 0.01f)
    }

    @Test
    fun faceToFace_throwUpArrivesFromTop() {
        val sender = flat(0f)
        val receiver = flat(180f) // Facing the sender across a table.
        val bearing = ThrowGeometry.screenToBearing(sender, 0f, -1f)!!
        val (sx, sy) = ThrowGeometry.bearingToScreen(receiver, bearing)!!
        assertEquals(0f, sx, 0.01f)
        assertEquals(1f, sy, 0.01f) // Travels down the receiver's screen, i.e. enters at the top.
    }

    @Test
    fun uwbToBearing_straightAheadAndRight() {
        val r = upright(0f)
        assertBearing(0f, ThrowGeometry.uwbToBearing(r, 0f, 0f))
        assertBearing(30f, ThrowGeometry.uwbToBearing(r, 30f, null))
    }

    @Test
    fun entryPoint_comesFromOppositeEdge() {
        // Moving down: enters at the top center.
        val (x, y) = ThrowGeometry.entryPoint(0f, 1f, 1000f, 2000f, inset = 100f)
        assertEquals(500f, x, 0.01f)
        assertEquals(100f, y, 0.01f)
        // Moving left: enters at the right edge.
        val (x2, y2) = ThrowGeometry.entryPoint(-1f, 0f, 1000f, 2000f, inset = 100f)
        assertEquals(900f, x2, 0.01f)
        assertEquals(1000f, y2, 0.01f)
    }

    @Test
    fun pickTarget_compassPicksPeerFacingBack() {
        val peers = listOf(
            TargetCandidate("facingBack", forwardBearing = 175f, uwbBearing = null),
            TargetCandidate("sideways", forwardBearing = 90f, uwbBearing = null),
        )
        assertEquals("facingBack", ThrowTargeting.pickTarget(0f, peers))
        // Neither peer faces back towards a throw to the East.
        assertNull(ThrowTargeting.pickTarget(90f, peers))
    }

    @Test
    fun pickTarget_uwbWinsOverCompass() {
        val peers = listOf(
            TargetCandidate("compass", forwardBearing = 180f, uwbBearing = null),
            TargetCandidate("uwb", forwardBearing = 90f, uwbBearing = 10f),
        )
        assertEquals("uwb", ThrowTargeting.pickTarget(0f, peers))
    }

    @Test
    fun pickTarget_fallbacks() {
        val unknown = TargetCandidate("only", forwardBearing = null, uwbBearing = null)
        assertEquals("only", ThrowTargeting.pickTarget(null, listOf(unknown)))
        assertEquals("only", ThrowTargeting.pickTarget(45f, listOf(unknown)))
        // A single peer gets the shape even when it doesn't face the thrower.
        val sideBySide = TargetCandidate("friend", forwardBearing = 0f, uwbBearing = null)
        assertEquals("friend", ThrowTargeting.pickTarget(0f, listOf(sideBySide)))
        assertNull(ThrowTargeting.pickTarget(null, listOf(unknown, unknown.copy(id = "other"))))
        assertNull(ThrowTargeting.pickTarget(0f, emptyList()))
    }
}
