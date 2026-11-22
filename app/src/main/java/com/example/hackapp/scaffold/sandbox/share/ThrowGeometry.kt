package com.example.hackapp.scaffold.sandbox.share

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pure math for throwing shapes between devices. Framework-free so it can be unit tested.
 *
 * Conventions:
 * - **Rotation matrix** `r`: row-major 3x3 from `SensorManager.getRotationMatrixFromVector`,
 *   mapping device coordinates (x right, y towards the top of the screen, z out of the
 *   screen) to world coordinates (x East, y North, z Up). Column `j` is device axis `j`
 *   expressed in world coordinates.
 * - **Screen vectors** use Compose coordinates: x right, y **down**.
 * - **Bearings** are compass degrees in `[0, 360)`: 0 = North, 90 = East.
 */
object ThrowGeometry {

    /** Horizontal (East, North) components below this length are too vertical to use. */
    private const val MIN_HORIZONTAL = 0.15f

    /**
     * World bearing of the device's "forward" direction: where the top of the screen points
     * when the phone lies flat, and where the back of the phone points when held upright.
     * Both views agree for in-between tilts, so they are summed for stability.
     */
    fun forwardBearing(r: FloatArray): Float? {
        val (fe, fn) = forwardHorizontal(r) ?: return null
        return bearingOf(fe, fn)
    }

    /**
     * World bearing of a direction drawn on screen ([sx], [sy] in Compose coordinates).
     * Swiping towards the top of the screen means "forward", see [forwardBearing].
     */
    fun screenToBearing(r: FloatArray, sx: Float, sy: Float): Float? {
        val (re, rn) = rightHorizontal(r) ?: return null
        val (fe, fn) = forwardHorizontal(r) ?: return null
        val up = -sy // Compose y grows downwards; device y grows towards the top.
        val e = sx * re + up * fe
        val n = sx * rn + up * fn
        if (hypot(e, n) < 1e-4f) return null
        return bearingOf(e, n)
    }

    /**
     * Inverse of [screenToBearing]: the unit screen direction (Compose coordinates) of
     * something travelling along world [bearingDeg] as seen on this device.
     */
    fun bearingToScreen(r: FloatArray, bearingDeg: Float): Pair<Float, Float>? {
        val (re, rn) = rightHorizontal(r) ?: return null
        val (fe, fn) = forwardHorizontal(r) ?: return null
        val rad = Math.toRadians(bearingDeg.toDouble())
        val e = sin(rad).toFloat()
        val n = cos(rad).toFloat()
        val sx = e * re + n * rn
        val up = e * fe + n * fn
        val len = hypot(sx, up)
        if (len < 1e-4f) return null
        return (sx / len) to (-up / len)
    }

    /**
     * World bearing to a peer measured by UWB angle of arrival.
     *
     * UWB reports the peer's direction relative to the back of the phone: azimuth is the
     * left/right angle (positive = right) and elevation the up/down angle (positive = up).
     */
    fun uwbToBearing(r: FloatArray, azimuthDeg: Float, elevationDeg: Float?): Float? {
        val az = Math.toRadians(azimuthDeg.toDouble())
        val el = Math.toRadians((elevationDeg ?: 0f).toDouble())
        // Direction in device coordinates (the back of the phone is -z).
        val dx = (sin(az) * cos(el)).toFloat()
        val dy = sin(el).toFloat()
        val dz = (-cos(az) * cos(el)).toFloat()
        val e = r[0] * dx + r[1] * dy + r[2] * dz
        val n = r[3] * dx + r[4] * dy + r[5] * dz
        if (hypot(e, n) < MIN_HORIZONTAL) return null
        return bearingOf(e, n)
    }

    /** Signed smallest difference `a - b` in degrees, in `(-180, 180]`. */
    fun angleDiff(a: Float, b: Float): Float {
        var d = (a - b) % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /**
     * Where a shape entering along screen direction ([dx], [dy]) should appear: on the screen
     * edge it comes *from*, inset by [inset] pixels so the shape starts fully on screen.
     */
    fun entryPoint(
        dx: Float,
        dy: Float,
        widthPx: Float,
        heightPx: Float,
        inset: Float,
    ): Pair<Float, Float> {
        val cx = widthPx / 2f
        val cy = heightPx / 2f
        val halfW = (cx - inset).coerceAtLeast(0f)
        val halfH = (cy - inset).coerceAtLeast(0f)
        // Distance along -direction from the center to the inset rectangle's border.
        val tx = if (abs(dx) > 1e-4f) halfW / abs(dx) else Float.MAX_VALUE
        val ty = if (abs(dy) > 1e-4f) halfH / abs(dy) else Float.MAX_VALUE
        val t = minOf(tx, ty).let { if (it == Float.MAX_VALUE) 0f else it }
        return (cx - dx * t) to (cy - dy * t)
    }

    private fun rightHorizontal(r: FloatArray): Pair<Float, Float>? =
        normalized(r[0], r[3])

    private fun forwardHorizontal(r: FloatArray): Pair<Float, Float>? =
        normalized(r[1] - r[2], r[4] - r[5])

    private fun normalized(e: Float, n: Float): Pair<Float, Float>? {
        val len = hypot(e, n)
        if (len < MIN_HORIZONTAL) return null
        return (e / len) to (n / len)
    }

    private fun bearingOf(e: Float, n: Float): Float {
        val deg = Math.toDegrees(atan2(e.toDouble(), n.toDouble())).toFloat()
        return (deg + 360f) % 360f
    }
}

/** What is known about a connected peer when a shape is thrown. */
data class TargetCandidate(
    val id: String,
    /** The peer's own [ThrowGeometry.forwardBearing], or `null` without a compass. */
    val forwardBearing: Float?,
    /** World bearing from this device to the peer measured with UWB, if fresh. */
    val uwbBearing: Float?,
)

object ThrowTargeting {
    /** A UWB-located peer must be within this many degrees of the throw. */
    const val UWB_CONE_DEG = 40f

    /** A compass-only peer must face back towards the thrower within this many degrees. */
    const val COMPASS_CONE_DEG = 55f

    /**
     * Picks which peer receives a shape thrown along [throwBearing], or `null` to let the
     * shape bounce off the wall as usual.
     *
     * 1. **UWB**: peers whose measured direction is closest to the throw win.
     * 2. **Compass**: phones held face to face point in opposite directions, so the peer whose
     *    forward bearing is closest to the throw's reverse wins.
     * 3. **Fallback**: with a single peer and no usable direction data, it gets the shape.
     */
    fun pickTarget(throwBearing: Float?, peers: List<TargetCandidate>): String? {
        if (peers.isEmpty()) return null
        if (throwBearing == null) return peers.singleOrNull()?.id

        val uwbHit = peers
            .mapNotNull { p -> p.uwbBearing?.let { p to abs(ThrowGeometry.angleDiff(it, throwBearing)) } }
            .filter { it.second <= UWB_CONE_DEG }
            .minByOrNull { it.second }
        if (uwbHit != null) return uwbHit.first.id

        val reverse = throwBearing + 180f
        val compassHit = peers
            .filter { it.uwbBearing == null }
            .mapNotNull { p -> p.forwardBearing?.let { p to abs(ThrowGeometry.angleDiff(it, reverse)) } }
            .filter { it.second <= COMPASS_CONE_DEG }
            .minByOrNull { it.second }
        if (compassHit != null) return compassHit.first.id

        val only = peers.singleOrNull() ?: return null
        return if (only.forwardBearing == null && only.uwbBearing == null) only.id else null
    }
}
