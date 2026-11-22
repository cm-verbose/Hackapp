package com.example.hackapp.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.hackapp.ui.theme.HackappCorners
import kotlinx.coroutines.launch

private const val SHEET_SLIDE_MILLIS = 320
private const val DISMISS_FRACTION = 0.25f
private const val DISMISS_VELOCITY = 1_800f

/**
 * A sheet that slides up to fill the screen, under the status bar, and slides back down when
 * swiped down from its handle or top area, dismissed with Back, or tapped outside. Unlike the
 * Material bottom sheet, it is drawn in the app window, so its bottom actions always clear the
 * navigation bar.
 *
 * [content] fills the sheet. Give the scrolling part `Modifier.weight(1f)` so the actions below it
 * stay pinned at the bottom.
 */
@Composable
fun FullScreenSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxSize().zIndex(10f)) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val offset = remember { Animatable(heightPx) }
        LaunchedEffect(Unit) { offset.animateTo(0f, tween(SHEET_SLIDE_MILLIS)) }
        fun close() {
            scope.launch {
                offset.animateTo(heightPx, tween(SHEET_SLIDE_MILLIS))
                onDismiss()
            }
        }
        BackHandler { close() }
        val scrimAlpha = (1f - offset.value / heightPx).coerceIn(0f, 1f) * 0.4f
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
        )
        val drag = rememberDraggableState { delta ->
            scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) }
        }
        Surface(
            shape = RoundedCornerShape(topStart = HackappCorners.ExtraLarge, topEnd = HackappCorners.ExtraLarge),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp)
                .graphicsLayer { translationY = offset.value }
                .semantics { dismiss { close(); true } },
        ) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                // The handle strip is where the swipe down starts, so scrolling content never fights it.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .draggable(
                            state = drag,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                if (offset.value > heightPx * DISMISS_FRACTION || velocity > DISMISS_VELOCITY) {
                                    close()
                                } else {
                                    offset.animateTo(0f, tween(SHEET_SLIDE_MILLIS / 2))
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 32.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                    )
                }
                content()
            }
        }
    }
}
