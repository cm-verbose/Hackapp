package com.example.hackapp.scaffold.screens.passport.sandbox.shape

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import org.jbox2d.dynamics.Body

const val PIXELS_PER_METER = 100;

fun Float.toMeters(): Float = this / PIXELS_PER_METER
fun Float.toPixels(): Float = this * PIXELS_PER_METER

/**
 * A physical shape that can be interacted with. It is based on a [Shape]
 * so it can be rendered and collided accurately.
 *
 * @param shape    The Material shape used as reference for rendering.
 * @param widthPx  The width of the shape in pixels.
 * @param heightPx The height of the shape in pixels.
 * @param body     The underlying Box2D rigid body.
 */
class PhysicalShape(
    val shape: Shape,
    val widthPx: Float,
    val heightPx: Float,
    val body: Body,
) {
    /** Center position of the shape on the X axis (in pixels). */
    var pX by mutableFloatStateOf(body.position.x.toPixels())
        private set

    /** Center position of the shape on the Y axis (in pixels). */
    var pY by mutableFloatStateOf(body.position.y.toPixels())
        private set

    /** Rotation in degrees. */
    var rotationDegrees by mutableFloatStateOf(
        Math.toDegrees(body.angle.toDouble()).toFloat()
    )
        private set

    /** Synchronize Box2D physics state into Compose UI state. */
    fun syncFromBody() {
        pX = body.position.x.toPixels()
        pY = body.position.y.toPixels()
        rotationDegrees = Math.toDegrees(body.angle.toDouble()).toFloat()
    }
}
