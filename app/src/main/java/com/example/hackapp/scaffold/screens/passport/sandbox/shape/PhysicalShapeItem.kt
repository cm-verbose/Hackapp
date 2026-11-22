package com.example.hackapp.scaffold.screens.passport.sandbox.shape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PhysicalShapeItem(
    physicalShape: PhysicalShape,
    color: Color,
    shadowElevation: Dp = 4.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val widthDp = with(density) { physicalShape.widthPx.toDp() }
    val heightDp = with(density) { physicalShape.heightPx.toDp() }

    Surface(
        modifier = Modifier
            .size(widthDp, heightDp)
            .graphicsLayer {
                // Shift from center-origin (Box2D) to top-left origin (Compose)
                translationX = physicalShape.pX - (physicalShape.widthPx / 2f)
                translationY = physicalShape.pY - (physicalShape.heightPx / 2f)
                rotationZ = physicalShape.rotationDegrees
            },
        shape = physicalShape.shape,
        color = color,
        shadowElevation = shadowElevation,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}
