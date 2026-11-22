package com.example.hackapp.scaffold.sandbox

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.hackapp.scaffold.sandbox.shape.PhysicalShapeItem
import com.example.hackapp.scaffold.sandbox.share.NearbyShare
import com.example.hackapp.scaffold.sandbox.share.ThrowGeometry
import com.example.hackapp.scaffold.sandbox.share.rememberDeviceOrientation
import com.example.hackapp.scaffold.sandbox.share.rememberNearbyShare
import com.example.hackapp.scaffold.sandbox.share.rememberShareHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jbox2d.common.Vec2

private const val FREE_LINEAR_DAMPING = 0.05f
private const val FREE_ANGULAR_DAMPING = 0.1f
private const val LOCKED_LINEAR_DAMPING = 1.5f
private const val LOCKED_ANGULAR_DAMPING = 1.5f
private const val HEADING_BROADCAST_MS = 300L

/**
 * After the "ready to send" vibration, the finger must slow below this fraction of the throw
 * speed before it can play again, so hovering around the threshold doesn't buzz repeatedly.
 */
private const val READY_REARM_FRACTION = 0.6f

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

            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    // The last row of the rotation matrix is the world "up" axis expressed in
                    // device coordinates, i.e. what the accelerometer reads at rest divided by g.
                    // Reading it directly avoids getOrientation()'s gimbal lock: its roll angle
                    // becomes unstable when the phone is held upright, which made held shapes
                    // flip around instead of staying readable.
                    val upX = rotationMatrix[6]
                    val upY = rotationMatrix[7]

                    // Same convention as the accelerometer branch below:
                    // Upright phone => upY > 0 => gy > 0 (pulls down to bottom wall)
                    // Tilting right => upX < 0 => gx > 0 (pulls right)
                    tiltState.value = TiltState(
                        gx = (-upX).coerceIn(-1f, 1f),
                        gy = upY.coerceIn(-1f, 1f)
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
/**
 * @param shareWithNearby When `true`, fast throws against a wall send the shape to a nearby
 *   device in that direction (Nearby Connections, aimed with the compass or UWB).
 */
@Composable
fun PhysicsScreen(
    modifier: Modifier = Modifier,
    items: List<SandboxItem> = defaultSandboxItems(),
    gravityLocked: Boolean = false,
    shareWithNearby: Boolean = true,
    snackbarHostState: SnackbarHostState? = null,
) {
    val tilt by rememberTiltState()
    // Read from inside the long-running simulation loop without restarting it.
    val isGravityLocked by rememberUpdatedState(gravityLocked)
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val orientation = rememberDeviceOrientation()
    val share = rememberNearbyShare(enabled = shareWithNearby)
    val currentShare by rememberUpdatedState(share)
    // Use the caller's host (e.g. a Scaffold's, which avoids FABs) or show our own.
    val ownSnackbarHostState = remember { SnackbarHostState() }
    val messages = rememberMessenger(snackbarHostState ?: ownSnackbarHostState)
    val shareHaptics = rememberShareHaptics()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF12141D))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // 1. Initialize the Box2D World and all physical shapes once screen size is known
        val sandbox = remember(items, widthPx, heightPx) {
            SandboxWorld(items, widthPx, heightPx, density).apply {
                populate(FREE_LINEAR_DAMPING, FREE_ANGULAR_DAMPING)
            }
        }
        val world = sandbox.world
        val grabber = remember(world) { ShapeGrabber(world) }

        // 2. Step the simulation every frame based on device tilt
        LaunchedEffect(sandbox) {
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
            var appliedLock: Boolean? = null

            while (true) {
                val nowNanos = withFrameNanos { it }
                val frameDt = ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                lastFrameNanos = nowNanos

                val locked = isGravityLocked
                if (locked != appliedLock) {
                    appliedLock = locked
                    // While locked, extra damping lets the shapes glide to a stop instead of
                    // drifting and bouncing around indefinitely in zero gravity.
                    sandbox.setDamping(
                        linear = if (locked) LOCKED_LINEAR_DAMPING else FREE_LINEAR_DAMPING,
                        angular = if (locked) LOCKED_ANGULAR_DAMPING else FREE_ANGULAR_DAMPING,
                    )
                }

                // Locked: gravity no longer applies. Unlocked: gravity follows device tilt.
                // Either transition changes gravity, which wakes the bodies below.
                val gravity = if (locked) {
                    Vec2(0f, 0f)
                } else {
                    Vec2(tilt.gx * gravityStrength, tilt.gy * gravityStrength)
                }
                world.gravity = gravity

                val gravityChanged = lastWakeGravity.x.isNaN() ||
                    gravity.sub(lastWakeGravity).length() > wakeThreshold
                if (gravityChanged) {
                    lastWakeGravity.set(gravity)
                    sandbox.wakeAll()
                }

                accumulator += frameDt
                var steps = 0
                while (accumulator >= fixedDt && steps < maxStepsPerFrame) {
                    grabber.update(fixedDt)
                    sandbox.checkThrow(nowNanos, currentShare, orientation.rotationMatrix)
                        ?.let { messages.show(it.message()) }
                    world.step(fixedDt, 8, 3)
                    accumulator -= fixedDt
                    steps++
                }
                // Drop leftover time after a long stall instead of spiraling.
                if (steps == maxStepsPerFrame) accumulator = 0f

                sandbox.update(nowNanos)
                sandbox.items.forEach { it.physicalShape.syncFromBody() }
            }
        }

        // 3. Exchange shapes and headings with nearby devices.
        if (share != null) {
            LaunchedEffect(share, sandbox) {
                share.incomingThrows.collect { incoming ->
                    val spec = sandbox.receive(
                        incoming.message,
                        orientation.rotationMatrix,
                        grabber.grabbed,
                    )
                    shareHaptics.received()
                    messages.show(
                        if (spec != null) {
                            "Received ${spec.displayName()} from ${incoming.fromName}"
                        } else {
                            "Received an unknown item from ${incoming.fromName}"
                        }
                    )
                }
            }
            LaunchedEffect(share) {
                while (true) {
                    share.broadcastHeading(orientation.rotationMatrix?.let(ThrowGeometry::forwardBearing))
                    delay(HEADING_BROADCAST_MS)
                }
            }
        }

        // 4. Render each composable shape. Long-press a shape to pick it up: it follows the
        //    finger and turns so its content reads upright for the current device orientation.
        //    Releasing it mid-swipe throws it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(grabber, sandbox) {
                    // The finger's speed, not the shape's, decides a throw: a shape flicked
                    // against the edge is stopped by the wall while the finger keeps going.
                    val fingerVelocity = VelocityTracker()
                    // Whether the held shape is currently moving fast enough to be sent.
                    var readyToSend = false
                    detectDragGesturesAfterLongPress(
                        onDragStart = { position ->
                            fingerVelocity.resetTracking()
                            readyToSend = false
                            if (grabber.grab(sandbox.grabbableShapes(), position)) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { change, _ ->
                            if (grabber.grabbed != null) {
                                change.consume()
                                fingerVelocity.addPosition(change.uptimeMillis, change.position)
                                grabber.moveTo(change.position)

                                // Buzz once when letting go now would send the shape to a
                                // nearby device; re-arm once the finger clearly slows down.
                                val v = fingerVelocity.calculateVelocity()
                                val velocity = Offset(v.x, v.y)
                                val hasPeers = currentShare?.peers?.isNotEmpty() == true
                                if (!readyToSend && hasPeers && sandbox.isThrowSpeed(velocity)) {
                                    readyToSend = true
                                    shareHaptics.readyToSend()
                                } else if (readyToSend && !sandbox.isThrowSpeed(velocity / READY_REARM_FRACTION)) {
                                    readyToSend = false
                                }
                            }
                        },
                        onDragEnd = {
                            val v = fingerVelocity.calculateVelocity()
                            sandbox.onReleased(grabber.release(), Offset(v.x, v.y))
                        },
                        onDragCancel = { grabber.release() },
                    )
                }
        ) {
            sandbox.items.forEach { item ->
                key(item.id) {
                    val appear = remember { Animatable(if (item.popIn) 0f else 1f) }
                    LaunchedEffect(Unit) {
                        appear.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                    }
                    val isGrabbed = grabber.grabbed === item.physicalShape
                    PhysicalShapeItem(
                        physicalShape = item.physicalShape,
                        color = item.spec.color,
                        shadowElevation = if (isGrabbed) 16.dp else 4.dp,
                        scale = { appear.value },
                    ) {
                        item.spec.content(this)
                    }
                }
            }
        }

        if (shareWithNearby) {
            NearbyStatus(
                share = share,
                compassUnreliable = orientation.compassUnreliable,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
        }

        if (snackbarHostState == null) {
            SnackbarHost(ownSnackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

private fun SandboxItem.displayName(): String = label ?: "a shape"

/** Snackbar text telling the thrower what happened to their shape. */
private fun ThrowOutcome.message(): String {
    val name = item.spec.displayName()
    return when (this) {
        is ThrowOutcome.Sent -> "Sent $name to $peerName"
        is ThrowOutcome.SharingOff -> "Nearby sharing is off: allow the Nearby devices permission"
        is ThrowOutcome.NoPeers -> "No nearby device connected yet"
        is ThrowOutcome.NoTargetInDirection -> "No device in that direction"
    }
}

/**
 * Shows short snackbars from non-suspending code (the simulation loop), replacing the current
 * one so rapid throws don't queue up behind each other.
 */
private class Messenger(
    private val scope: CoroutineScope,
    private val hostState: SnackbarHostState,
) {
    private var current: Job? = null

    fun show(text: String) {
        current?.cancel() // Cancelling showSnackbar dismisses its snackbar.
        current = scope.launch { hostState.showSnackbar(text, duration = SnackbarDuration.Short) }
    }
}

@Composable
private fun rememberMessenger(hostState: SnackbarHostState): Messenger {
    val scope = rememberCoroutineScope()
    return remember(scope, hostState) { Messenger(scope, hostState) }
}

/** Small pill showing whether shapes can be thrown, and to how many devices. */
@Composable
private fun NearbyStatus(
    share: NearbyShare?,
    compassUnreliable: Boolean,
    modifier: Modifier = Modifier,
) {
    val peers = share?.peers?.values.orEmpty()
    val withUwb = peers.count { it.uwbAzimuthDeg != null }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.12f),
        contentColor = Color.White,
    ) {
        Text(
            text = when {
                share == null -> "Nearby sharing off (permission needed)"
                share.error != null -> share.error!!
                peers.isEmpty() -> "Searching for nearby devices…"
                else -> buildString {
                    append("${peers.size} nearby: ")
                    append(peers.joinToString { it.name })
                    if (withUwb > 0) append(" · UWB")
                    // Aiming only matters when there are several devices to choose from.
                    if (compassUnreliable && peers.size > 1) {
                        append("\nCompass inaccurate: move the phone in a figure 8")
                    }
                }
            },
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
