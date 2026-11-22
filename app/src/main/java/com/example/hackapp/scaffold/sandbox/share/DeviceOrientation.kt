package com.example.hackapp.scaffold.sandbox.share

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect

/**
 * Latest absolute device orientation, from the magnetometer-backed rotation vector sensor.
 * Remapped to the current display rotation so "screen x/y" match what the user sees.
 */
class DeviceOrientation {
    /** Row-major device→world rotation matrix, see [ThrowGeometry]. `null` until a reading arrives. */
    var rotationMatrix: FloatArray? = null
        internal set

    /**
     * `true` while the sensor reports its heading as unreliable, meaning throws may be aimed
     * at the wrong device. Moving the phone in a figure 8 recalibrates the magnetometer.
     */
    var compassUnreliable by mutableStateOf(false)
        internal set
}

/** Heading error (degrees) above which the compass is reported as unreliable. */
private const val MAX_HEADING_ERROR_DEG = 30.0

/** Listens only while [enabled] and the page is started. */
@Composable
fun rememberDeviceOrientation(enabled: Boolean): DeviceOrientation {
    val context = LocalContext.current
    val orientation = remember { DeviceOrientation() }

    LifecycleStartEffect(context, enabled) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        // TYPE_ROTATION_VECTOR fuses the magnetometer, so its azimuth is relative to North.
        // TYPE_GAME_ROTATION_VECTOR would drift and can't be compared across devices.
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val listener = object : SensorEventListener {
            private val raw = FloatArray(9)

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(raw, event.values)
                val (axisX, axisY) = when (ContextCompat.getDisplayOrDefault(context).rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                val remapped = FloatArray(9)
                SensorManager.remapCoordinateSystem(raw, axisX, axisY, remapped)
                orientation.rotationMatrix = remapped

                // values[4], when present, is the estimated heading accuracy in radians
                // (-1 if unknown); event.accuracy is the coarser status of the fusion.
                val headingErrorDeg = event.values.getOrNull(4)
                    ?.takeIf { it >= 0f }
                    ?.let { Math.toDegrees(it.toDouble()) }
                orientation.compassUnreliable =
                    event.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW ||
                    (headingErrorDeg != null && headingErrorDeg > MAX_HEADING_ERROR_DEG)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (enabled) sensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onStopOrDispose { sensorManager?.unregisterListener(listener) }
    }

    return orientation
}
