package com.example.hackapp.scaffold.screens.planning

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme
import kotlin.math.roundToInt

private val RowGap = 6.dp

/**
 * The selected Districts in visit order. Drag a row by its handle, or long press it, to move it;
 * the order is saved when the finger lifts. TalkBack gets Move up and Move down actions instead.
 * A tap on a row opens its District sheet, as on the map.
 */
@Composable
internal fun ReorderableDistrictList(
    selected: List<String>,
    nameOf: (String) -> String,
    onOpen: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val gapPx = with(LocalDensity.current) { RowGap.toPx() }
    // While a row is dragged, the list shows the order it would drop into.
    var order by remember(selected) { mutableStateOf(selected) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(0f) }
    var startIndex by remember { mutableStateOf(-1) }
    val moveUp = stringResource(R.string.district_move_up)
    val moveDown = stringResource(R.string.district_move_down)

    fun startDrag(id: String) {
        draggedId = id
        startIndex = order.indexOf(id)
        dragOffset = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun drag(delta: Float) {
        val id = draggedId ?: return
        dragOffset += delta
        val step = rowHeight.takeIf { it > 0f } ?: return
        val index = order.indexOf(id)
        val target = (index + (dragOffset / step).roundToInt()).coerceIn(0, order.lastIndex)
        if (target != index) {
            order = order.toMutableList().apply { add(target, removeAt(index)) }
            dragOffset -= (target - index) * step
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    fun endDrag() {
        val id = draggedId ?: return
        val end = order.indexOf(id)
        draggedId = null
        dragOffset = 0f
        if (end != startIndex && startIndex >= 0) onMove(startIndex, end)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        order.forEachIndexed { index, id ->
            key(id) {
                val dragging = id == draggedId
                val name = nameOf(id)
                Surface(
                    onClick = { onOpen(id) },
                    shape = MaterialTheme.shapes.small,
                    color = if (dragging) scheme.primaryContainer else scheme.secondaryContainer,
                    contentColor = if (dragging) scheme.onPrimaryContainer else scheme.onSecondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .then(if (dragging) Modifier.shadow(6.dp, MaterialTheme.shapes.small) else Modifier)
                        .onSizeChanged { rowHeight = it.height + gapPx }
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { startDrag(id) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag(amount.y)
                                },
                                onDragEnd = { endDrag() },
                                onDragCancel = { endDrag() },
                            )
                        }
                        .semantics {
                            customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction(moveUp) { onMove(index, index - 1); true })
                                if (index < order.lastIndex) add(CustomAccessibilityAction(moveDown) { onMove(index, index + 1); true })
                            }
                        },
                ) {
                    Row(
                        Modifier
                            .heightIn(min = 48.dp)
                            .padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(shape = CircleShape, color = scheme.primary, contentColor = scheme.onPrimary) {
                            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Text(
                            name,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        // The handle drags at once, without the long press.
                        Box(
                            Modifier
                                .size(48.dp)
                                .pointerInput(id) {
                                    detectVerticalDragGestures(
                                        onDragStart = { startDrag(id) },
                                        onVerticalDrag = { change, amount ->
                                            change.consume()
                                            drag(amount)
                                        },
                                        onDragEnd = { endDrag() },
                                        onDragCancel = { endDrag() },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.DragHandle, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Reorderable Districts", showBackground = true, widthDp = 360, fontScale = 1.3f)
@Composable
private fun ReorderableDistrictListPreview() = HackappTheme {
    ReorderableDistrictList(
        selected = listOf("ville-marie", "plateau-mont-royal", "verdun"),
        nameOf = { mapOf("ville-marie" to "Ville-Marie", "plateau-mont-royal" to "Le Plateau-Mont-Royal", "verdun" to "Verdun")[it] ?: it },
        onOpen = {},
        onMove = { _, _ -> },
        modifier = Modifier.padding(16.dp),
    )
}
