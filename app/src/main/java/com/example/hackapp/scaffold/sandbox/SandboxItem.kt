package com.example.hackapp.scaffold.sandbox

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Specification for a composable item participating in the physics sandbox simulation.
 *
 * @param shape The Compose [Shape] defining the rendering and collision outline.
 * @param width The width of the item in [Dp].
 * @param height The height of the item in [Dp].
 * @param color The background color of the item surface.
 * @param initialX Optional initial X position in [Dp]. If null, items are arranged around the screen center.
 * @param initialY Optional initial Y position in [Dp]. If null, items are arranged around the screen center.
 * @param restitution Bounciness factor of the physical body (0.0 = no bounce, 1.0 = fully elastic).
 * @param friction Surface friction coefficient of the physical body.
 * @param content The composable UI content displayed inside the physical shape.
 */
data class SandboxItem(
    val shape: Shape = RoundedCornerShape(16.dp),
    val width: Dp = 80.dp,
    val height: Dp = 80.dp,
    val color: Color = Color(0xFF1E88E5),
    val initialX: Dp? = null,
    val initialY: Dp? = null,
    val restitution: Float = 0.5f,
    val friction: Float = 0.3f,
    val content: @Composable BoxScope.() -> Unit = {}
)
