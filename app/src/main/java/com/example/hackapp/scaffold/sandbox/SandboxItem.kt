package com.example.hackapp.scaffold.sandbox

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Specification for a composable item participating in the physics sandbox simulation.
 *
 * @param key Stable identity. An item keeps its body and position while its key stays in the list.
 * @param color The background color of the item surface.
 * @param shape The Compose [Shape] defining the rendering and collision outline.
 * @param width The width of the item in [Dp].
 * @param height The height of the item in [Dp].
 * @param restitution Bounciness factor of the physical body (0.0 = no bounce, 1.0 = fully elastic).
 * @param startCenterPx Where the item first appears, as its centre in the world's pixels. Null
 *   picks a free spot. Only used when the world is first filled, not for later respawns.
 * @param content The composable UI content displayed inside the physical shape.
 */
data class SandboxItem(
    val key: Any,
    val color: Color,
    val shape: Shape = RoundedCornerShape(16.dp),
    val width: Dp = 80.dp,
    val height: Dp = 80.dp,
    val restitution: Float = 0.5f,
    val startCenterPx: Offset? = null,
    val content: @Composable BoxScope.() -> Unit = {},
)
