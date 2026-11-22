package com.example.hackapp.scaffold.sandbox.share

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Latest absolute device orientation, from the magnetometer-backed rotation vector sensor.
 * Remapped to the current display rotation so "screen x/y" match what the user sees.
 */
class DeviceOrientation {
    /** Row-major device→world rotation matrix, see [ThrowGeometry]. `null` until a reading arrives. */
    var rotationMatrix: FloatArray? = null
        internal set
}

@Composable
fun rememberDeviceOrientation(): DeviceOrientation {
    val context = LocalContext.current
    val orientation = remember { DeviceOrientation() }

    DisposableEffect(context) {
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
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    return orientation
}
