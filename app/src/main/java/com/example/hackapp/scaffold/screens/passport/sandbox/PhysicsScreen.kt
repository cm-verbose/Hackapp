package com.example.hackapp.scaffold.screens.passport.sandbox

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.example.hackapp.scaffold.screens.passport.sandbox.shape.PhysicalShape
import com.example.hackapp.scaffold.screens.passport.sandbox.shape.PhysicalShapeItem
import com.example.hackapp.scaffold.screens.passport.sandbox.simulatedworld.createBoundaryWalls
import com.example.hackapp.scaffold.screens.passport.sandbox.simulatedworld.createPhysicalShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.World

private data class CreatedPhysicsItem(
    val spec: SandboxItem,
    val physicalShape: PhysicalShape,
)

/** Normalized screen tilt vector (gx: left/right, gy: up/down). */
data class TiltState(
    val gx: Float = 0f,
    val gy: Float = 1f, // Default gravity pulls downwards towards bottom of screen
)

@Composable
fun rememberTiltState(): State<TiltState> {
    val context = LocalContext.current
    val tiltState = remember { mutableStateOf(TiltState()) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val rotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientation = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val pitch = orientation[1] // pitch around X-axis
                    val roll = orientation[2]  // roll around Y-axis

                    // Unify screen gravity:
                    // Upright phone => pitch < 0 => gy > 0 (pulls down to bottom wall)
                    // Tilting right => roll > 0 => gx > 0 (pulls right)
                    tiltState.value = TiltState(
                        gx = roll.coerceIn(-1f, 1f),
                        gy = (-pitch).coerceIn(-1f, 1f)
                    )
                } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val ax = event.values[0] / SensorManager.GRAVITY_EARTH
                    val ay = event.values[1] / SensorManager.GRAVITY_EARTH

                    // Upright phone => ay > 0 => gy > 0 (pulls down to bottom wall)
                    // Tilting right => ax < 0 => gx > 0 (pulls right)
                    tiltState.value = TiltState(
                        gx = (-ax).coerceIn(-1f, 1f),
                        gy = ay.coerceIn(-1f, 1f)
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val sensorToUse = rotationVector ?: accelerometer
        sensorToUse?.let {
            sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    return tiltState
}

@Composable
fun PhysicsScreen(
    modifier: Modifier = Modifier,
    items: List<SandboxItem> = defaultSandboxItems(),
) {
    val tilt by rememberTiltState()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF12141D))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val centerX = widthPx / 2f
        val centerY = heightPx / 2f

        // 1. Initialize the Box2D World and all physical shapes once screen size is known
        val (world, createdItems) = remember(items, widthPx, heightPx) {
            val physicsWorld = World(Vec2(0f, 9.8f))
            physicsWorld.createBoundaryWalls(widthPx, heightPx, density)

            val cols = 2
            val list = items.mapIndexed { index, spec ->
                val wPx = with(density) { spec.width.toPx() }
                val hPx = with(density) { spec.height.toPx() }

                val col = index % cols
                val row = index / cols

                val posX = spec.initialX?.let { with(density) { it.toPx() } }
                    ?: (centerX + (col - 0.5f) * (wPx + 80f))
                val posY = spec.initialY?.let { with(density) { it.toPx() } }
                    ?: (centerY + (row - 1f) * (hPx + 80f))

                val physicalShape = physicsWorld.createPhysicalShape(
                    shape = spec.shape,
                    density = density,
                    dimensionsPixels = wPx to hPx,
                    initialPositionPixels = posX to posY,
                    linearDamping = 0.05f,
                    angularDamping = 0.1f,
                    restitution = spec.restitution,
                    friction = 0.02f, // Low friction prevents shapes sticking to each other or walls
                )

                CreatedPhysicsItem(spec = spec, physicalShape = physicalShape)
            }

            physicsWorld to list
        }

        // 2. Step the simulation every frame based on device tilt
        LaunchedEffect(world, createdItems) {
            var lastFrameNanos = withFrameNanos { it }
            val gravityStrength = 28f

            // Box2D solves stacks far more reliably with a constant timestep, so the frame
            // delta is accumulated and consumed in fixed-size steps.
            val fixedDt = 1f / 60f
            val maxStepsPerFrame = 4
            var accumulator = 0f

            // Gravity at the moment bodies were last woken. Box2D puts bodies to sleep after
            // ~0.5s at rest, and World.setGravity() does NOT wake them, so once the device is
            // held still a resting pile goes to sleep and ignores any later tilt. Bodies are
            // woken explicitly whenever gravity has changed noticeably.
            val lastWakeGravity = Vec2(Float.NaN, Float.NaN)
            val wakeThreshold = 0.02f * gravityStrength

            while (true) {
                val nowNanos = withFrameNanos { it }
                val frameDt = ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                lastFrameNanos = nowNanos

                val gravity = Vec2(
                    tilt.gx * gravityStrength,
                    tilt.gy * gravityStrength,
                )
                world.gravity = gravity

                val gravityChanged = lastWakeGravity.x.isNaN() ||
                    gravity.sub(lastWakeGravity).length() > wakeThreshold
                if (gravityChanged) {
                    lastWakeGravity.set(gravity)
                    createdItems.forEach { it.physicalShape.body.isAwake = true }
                }

                accumulator += frameDt
                var steps = 0
                while (accumulator >= fixedDt && steps < maxStepsPerFrame) {
                    world.step(fixedDt, 8, 3)
                    accumulator -= fixedDt
                    steps++
                }
                // Drop leftover time after a long stall instead of spiralling.
                if (steps == maxStepsPerFrame) accumulator = 0f

                createdItems.forEach { it.physicalShape.syncFromBody() }
            }
        }

        // 3. Render each composable shape
        createdItems.forEach { item ->
            PhysicalShapeItem(
                physicalShape = item.physicalShape,
                color = item.spec.color,
            ) {
                item.spec.content(this)
            }
        }
    }
}
