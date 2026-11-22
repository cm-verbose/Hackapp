package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * Figma demo control (`186:2775`, wrapped `186:2786`): the simulated date and time, and the button
 * that jumps to the next moment. The button moves under the time when both do not fit on one line.
 */
@Composable
fun DemoControl(
    dateTime: String,
    canAdvance: Boolean,
    onNextMoment: () -> Unit,
    modifier: Modifier = Modifier,
    failed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    val colors = HackappTheme.appColors
    val shape = RoundedCornerShape(HackappCorners.Medium)
    // The clock also runs by itself: only a change right after a tap is read aloud.
    var jumps by remember { mutableIntStateOf(0) }
    var announceTime by remember { mutableStateOf(false) }
    LaunchedEffect(jumps) {
        if (jumps > 0) {
            delay(JUMP_ANNOUNCE_MILLIS)
            announceTime = false
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.warningContainer)
            .dashedBorder(colors.warning, HackappCorners.Medium)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        InlineOrStacked(
            first = { DemoTime(dateTime, announceTime) },
            second = {
                Button(
                    onClick = {
                        announceTime = true
                        jumps++
                        onNextMoment()
                    },
                    enabled = canAdvance,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.warning,
                        contentColor = colors.onWarning,
                    ),
                    modifier = Modifier.heightIn(min = 40.dp),
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.demo_next_moment),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            },
        )
        if (failed) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.demo_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onWarningContainer,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                HackappSmallTextButton(
                    stringResource(R.string.action_retry),
                    onRetry,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onWarningContainer),
                )
            }
        }
    }
}

@Composable
private fun DemoTime(dateTime: String, announce: Boolean) {
    val color = HackappTheme.appColors.onWarningContainer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Schedule, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Column {
            Text(stringResource(R.string.demo_mode_title), style = MaterialTheme.typography.labelMedium, color = color)
            Text(
                dateTime,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                modifier = Modifier.semantics { if (announce) liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** [first] and [second] on one line when they fit. Otherwise stacked, with [second] at full width. */
@Composable
private fun InlineOrStacked(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    Layout(
        content = {
            Box { first() }
            Box(propagateMinConstraints = true) { second() }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val width = constraints.maxWidth
        val firstWidth = measurables[0].maxIntrinsicWidth(Constraints.Infinity)
        val secondWidth = measurables[1].maxIntrinsicWidth(Constraints.Infinity)
        if (firstWidth + gap + secondWidth <= width) {
            val firstPlaced = measurables[0].measure(Constraints(maxWidth = width - gap - secondWidth))
            val secondPlaced = measurables[1].measure(Constraints(maxWidth = secondWidth))
            val height = max(firstPlaced.height, secondPlaced.height)
            layout(width, height) {
                firstPlaced.placeRelative(0, (height - firstPlaced.height) / 2)
                secondPlaced.placeRelative(width - secondPlaced.width, (height - secondPlaced.height) / 2)
            }
        } else {
            val firstPlaced = measurables[0].measure(Constraints(maxWidth = width))
            val secondPlaced = measurables[1].measure(Constraints(minWidth = width, maxWidth = width))
            layout(width, firstPlaced.height + gap + secondPlaced.height) {
                firstPlaced.placeRelative(0, 0)
                secondPlaced.placeRelative(0, firstPlaced.height + gap)
            }
        }
    }
}

private fun Modifier.dashedBorder(color: Color, corner: Dp): Modifier = drawBehind {
    val stroke = 1.5.dp.toPx()
    val dash = 5.dp.toPx()
    val inset = stroke / 2
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(corner.toPx() - inset),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
    )
}

/** How long after a tap a time change still counts as that jump. */
private const val JUMP_ANNOUNCE_MILLIS = 2_000L

private const val PreviewTime = "sam. 28 nov. · 18:42"

@Preview(name = "DemoControl", showBackground = true, widthDp = 412)
@Composable
private fun DemoControlPreview() = HackappTheme {
    DemoControl(PreviewTime, canAdvance = true, onNextMoment = {}, modifier = Modifier.padding(16.dp))
}

@Preview(name = "DemoControl · dark", showBackground = true, backgroundColor = 0xFF19120E, widthDp = 412)
@Composable
private fun DemoControlDarkPreview() = HackappTheme(darkTheme = true) {
    DemoControl(PreviewTime, canAdvance = true, onNextMoment = {}, modifier = Modifier.padding(16.dp))
}

@Preview(name = "DemoControl · wrapped, 360 dp at 1.3x", showBackground = true, widthDp = 360, fontScale = 1.3f)
@Composable
private fun DemoControlWrappedPreview() = HackappTheme {
    DemoControl(PreviewTime, canAdvance = true, onNextMoment = {}, modifier = Modifier.padding(16.dp))
}

@Preview(name = "DemoControl · nothing to advance", showBackground = true, widthDp = 412)
@Composable
private fun DemoControlDisabledPreview() = HackappTheme {
    DemoControl(PreviewTime, canAdvance = false, onNextMoment = {}, modifier = Modifier.padding(16.dp))
}

@Preview(name = "DemoControl · exit failed", showBackground = true, widthDp = 412)
@Composable
private fun DemoControlFailedPreview() = HackappTheme {
    DemoControl(PreviewTime, canAdvance = true, onNextMoment = {}, modifier = Modifier.padding(16.dp), failed = true)
}

@Preview(name = "DemoControl · exit failed, dark", showBackground = true, backgroundColor = 0xFF19120E, widthDp = 360, fontScale = 1.3f)
@Composable
private fun DemoControlFailedDarkPreview() = HackappTheme(darkTheme = true) {
    DemoControl(PreviewTime, canAdvance = true, onNextMoment = {}, modifier = Modifier.padding(16.dp), failed = true)
}
