package com.example.hackapp.scaffold.sandbox.shape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws a [PhysicalShape] at its simulated position and rotation.
 *
 * The drop shadow is a flat, offset silhouette of the shape rather than a Material elevation
 * shadow. Elevation shadows of concave outlines (the star and cookie shapes) are re-tessellated
 * by Skia every time the shape rotates, which is slow enough to freeze the render thread on
 * mid-range devices and trigger an ANR while shapes spin.
 *
 * @param shadowElevation How far the shadow is offset below the shape; larger looks lifted.
 */
@Composable
fun PhysicalShapeItem(
    physicalShape: PhysicalShape,
    color: Color,
    shadowElevation: Dp = 4.dp,
    scale: () -> Float = { 1f },
    content: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val widthDp = with(density) { physicalShape.widthPx.toDp() }
    val heightDp = with(density) { physicalShape.heightPx.toDp() }
    val shadowOffsetPx = with(density) { shadowElevation.toPx() } / 2f
    val shadowColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)

    Box(
        modifier = Modifier
            .size(widthDp, heightDp)
            .graphicsLayer {
                // Shift from center-origin (Box2D) to top-left origin (Compose)
                translationX = physicalShape.pX - (physicalShape.widthPx / 2f)
                translationY = physicalShape.pY - (physicalShape.heightPx / 2f)
                rotationZ = physicalShape.rotationDegrees
                scale().let {
                    scaleX = it
                    scaleY = it
                }
            },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    // Offset straight down on screen whatever the shape's rotation: the parent
                    // layer is rotated by θ, so the local offset is the screen offset rotated by -θ.
                    val theta = Math.toRadians(physicalShape.rotationDegrees.toDouble())
                    translationX = (shadowOffsetPx * sin(theta)).toFloat()
                    translationY = (shadowOffsetPx * cos(theta)).toFloat()
                }
                .background(shadowColor, physicalShape.shape),
        )
        Surface(
            modifier = Modifier.matchParentSize(),
            shape = physicalShape.shape,
            color = color,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
    }
}
