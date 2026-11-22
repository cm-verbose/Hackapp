package com.example.hackapp.scaffold.sandbox.share

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Vibrations for sharing shapes between devices.
 *
 * Uses the [Vibrator] directly rather than Compose haptic feedback: haptic feedback is
 * silently skipped when the user turned off "touch feedback" in the system settings, and a
 * shape arriving from another device must be noticeable.
 */
class ShareHaptics(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    /** Two quick ticks: the held shape is moving fast enough to be sent if released now. */
    fun readyToSend() = play(
        timings = longArrayOf(0, 18, 55, 18),
        amplitudes = intArrayOf(0, 140, 0, 200),
    )

    /** A firm knock followed by a short buzz: a shape arrived from another device. */
    fun received() = play(
        timings = longArrayOf(0, 45, 70, 140),
        amplitudes = intArrayOf(0, 255, 0, 120),
    )

    private fun play(timings: LongArray, amplitudes: IntArray) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        v.vibrate(effect)
    }
}

@Composable
fun rememberShareHaptics(): ShareHaptics {
    val context = LocalContext.current
    return remember(context) { ShareHaptics(context.applicationContext) }
}
