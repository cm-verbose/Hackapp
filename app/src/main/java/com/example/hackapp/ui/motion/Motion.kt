package com.example.hackapp.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/** Shared motion tokens: one curve family and a few durations, so the whole app moves the same way. */
object HackappMotion {
    /** Material "emphasized decelerate": things arriving. */
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Material "emphasized accelerate": things leaving. */
    val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val Short = 200
    const val Medium = 350
    const val Long = 500

    /** Playful overshoot for taps and pop-ins. */
    fun <T> bouncy() = spring<T>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
}

/** Shrinks slightly while pressed, then springs back. Pass the same [source] given to the clickable or button. */
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.95f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, HackappMotion.bouncy(), label = "pressScale")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Fades and rises into place once, [index] * [stepMillis] after the first composition (staggered lists). */
fun Modifier.entrance(index: Int = 0, stepMillis: Int = 70, rise: Float = 28f): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * stepMillis.toLong())
        progress.animateTo(1f, tween(HackappMotion.Long, easing = HackappMotion.Decelerate))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise * density
    }
}

/** Scales in from nothing with an overshoot, [delayMillis] after it appears (map markers, badges). */
fun Modifier.popIn(delayMillis: Int = 0): Modifier = composed {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        scale.animateTo(1f, HackappMotion.bouncy())
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        alpha = scale.value.coerceIn(0f, 1f)
    }
}

/** Gently grows and shrinks forever (selected pin, live status dot). */
fun Modifier.pulse(from: Float = 0.94f, to: Float = 1.06f, periodMillis: Int = 1400): Modifier = composed {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(
        from, to,
        infiniteRepeatable(tween(periodMillis, easing = HackappMotion.Decelerate), RepeatMode.Reverse),
        label = "pulseScale",
    )
    graphicsLayer { scaleX = s; scaleY = s }
}

/** Slow drifting colour blobs behind the content. Draws only; the content keeps its own background. */
fun Modifier.floatingOrbs(first: Color, second: Color, periodMillis: Int = 9000): Modifier = composed {
    val t = rememberInfiniteTransition(label = "orbs")
    val phase by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Reverse),
        label = "orbPhase",
    )
    drawBehind {
        val r = size.minDimension * 0.7f
        val c1 = Offset(size.width * (0.85f - 0.2f * phase), size.height * (0.1f + 0.3f * phase))
        drawCircle(Brush.radialGradient(listOf(first, Color.Transparent), c1, r), r, c1)
        val c2 = Offset(size.width * (0.1f + 0.25f * phase), size.height * (1.0f - 0.35f * phase))
        drawCircle(Brush.radialGradient(listOf(second, Color.Transparent), c2, r), r, c2)
    }
}

/** A soft highlight that sweeps across the surface now and then (call-to-action cards). */
fun Modifier.shimmerSweep(highlight: Color = Color.White.copy(alpha = 0.22f), periodMillis: Int = 3200): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        -0.5f, 1.5f,
        infiniteRepeatable(tween(periodMillis, easing = HackappMotion.Decelerate)),
        label = "shimmerX",
    )
    drawBehind {
        val start = Offset(size.width * x, 0f)
        val end = Offset(size.width * x + size.height, size.height)
        drawRect(Brush.linearGradient(listOf(Color.Transparent, highlight, Color.Transparent), start, end))
    }
}
