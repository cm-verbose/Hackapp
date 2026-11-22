package com.example.hackapp.scaffold.sandbox

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.hackapp.scaffold.sandbox.shape.PhysicalShapeItem
import com.example.hackapp.scaffold.sandbox.share.ThrowChannel
import com.example.hackapp.scaffold.sandbox.share.ThrowGeometry
import com.example.hackapp.scaffold.sandbox.share.rememberDeviceOrientation
import com.example.hackapp.scaffold.sandbox.share.rememberShareHaptics
import kotlinx.coroutines.delay
import org.jbox2d.common.Vec2

private const val FREE_LINEAR_DAMPING = 0.05f
private const val FREE_ANGULAR_DAMPING = 0.1f
private const val LOCKED_LINEAR_DAMPING = 1.5f
private const val LOCKED_ANGULAR_DAMPING = 1.5f
private const val HEADING_BROADCAST_MS = 300L

/** How often a world at rest checks for changes, instead of drawing every frame. */
private const val IDLE_POLL_MS = 100L

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

/** Reads the tilt only while [enabled] and the page is started. */
@Composable
fun rememberTiltState(enabled: Boolean): State<TiltState> {
    val context = LocalContext.current
    val tiltState = remember { mutableStateOf(TiltState()) }

    LifecycleStartEffect(context, enabled) {
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
                        gy = upY.coerceIn(-1f, 1f),
                    )
                } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val ax = event.values[0] / SensorManager.GRAVITY_EARTH
                    val ay = event.values[1] / SensorManager.GRAVITY_EARTH

                    // Upright phone => ay > 0 => gy > 0 (pulls down to bottom wall)
                    // Tilting right => ax < 0 => gx > 0 (pulls right)
                    tiltState.value = TiltState(
                        gx = (-ax).coerceIn(-1f, 1f),
                        gy = ay.coerceIn(-1f, 1f),
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (enabled) {
            (rotationVector ?: accelerometer)?.let {
                sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        onStopOrDispose { sensorManager?.unregisterListener(listener) }
    }

    return tiltState
}

/**
 * Turns on throwing objects to nearby devices. A null [channel] means sharing was chosen but is
 * not available, for example because a permission was denied.
 */
class SandboxSharing(
    val channel: ThrowChannel?,
    val onThrow: (ThrowOutcome) -> Unit,
    val onReceive: (item: SandboxItem?, fromName: String?) -> Unit,
)

/**
 * The physics world. It runs only while the page is started and stops drawing when at rest.
 *
 * @param items What to show, matched by [SandboxItem.key] so updates keep positions.
 * @param sharing Null keeps every object on this device: a fast flick simply bounces.
 * @param onItemClick Called when an object is tapped.
 */
@Composable
fun PhysicsScreen(
    items: List<SandboxItem>,
    modifier: Modifier = Modifier,
    gravityLocked: Boolean = false,
    sharing: SandboxSharing? = null,
    onItemClick: (SandboxItem) -> Unit = {},
) {
    val tilt by rememberTiltState(enabled = !gravityLocked)
    // Read from inside the long-running simulation loop without restarting it.
    val isGravityLocked by rememberUpdatedState(gravityLocked)
    val currentSharing by rememberUpdatedState(sharing)
    val currentOnItemClick by rememberUpdatedState(onItemClick)
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val orientation = rememberDeviceOrientation(enabled = sharing != null)
    val shareHaptics = rememberShareHaptics()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // 1. Initialize the Box2D World once the size is known. List changes go through sync.
        val sandbox = remember(widthPx, heightPx, density) {
            SandboxWorld(widthPx, heightPx, density).apply {
                populate(items, FREE_LINEAR_DAMPING, FREE_ANGULAR_DAMPING)
            }
        }
        val world = sandbox.world
        val grabber = remember(world) { ShapeGrabber(world) }

        LaunchedEffect(sandbox, items) {
            grabber.grabbed?.let { if (!sandbox.isListed(it, items)) grabber.release() }
            sandbox.sync(items)
        }

        // 2. Step the simulation every frame based on device tilt, only while the page is started.
        LaunchedEffect(sandbox, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                        val share = currentSharing
                        val outcome = sandbox.checkThrow(nowNanos, share?.channel, orientation.rotationMatrix)
                        if (outcome != null) share?.onThrow?.invoke(outcome)
                        world.step(fixedDt, 8, 3)
                        accumulator -= fixedDt
                        steps++
                    }
                    // Drop leftover time after a long stall instead of spiraling.
                    if (steps == maxStepsPerFrame) accumulator = 0f

                    sandbox.update(nowNanos)
                    sandbox.items.forEach { it.physicalShape.syncFromBody() }

                    // Nothing moves: stop asking for frames until something can change.
                    while (sandbox.isAtRest() && grabber.grabbed == null && isGravityLocked == appliedLock &&
                        (isGravityLocked || !tiltMoved(tilt, gravity, gravityStrength, wakeThreshold))
                    ) {
                        delay(IDLE_POLL_MS)
                        lastFrameNanos = withFrameNanos { it }
                    }
                }
            }
        }

        // 3. Exchange shapes and headings with nearby devices, only when sharing is on.
        val channel = sharing?.channel
        if (channel != null) {
            LaunchedEffect(channel, sandbox) {
                channel.incomingThrows.collect { incoming ->
                    val spec = sandbox.receive(incoming.message, orientation.rotationMatrix, grabber.grabbed)
                    shareHaptics.received()
                    currentSharing?.onReceive?.invoke(spec, incoming.fromName)
                }
            }
            LaunchedEffect(channel, lifecycle) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) {
                        channel.broadcastHeading(orientation.rotationMatrix?.let(ThrowGeometry::forwardBearing))
                        delay(HEADING_BROADCAST_MS)
                    }
                }
            }
        }

        // 4. Render each shape. Tap to open it. Long-press to pick it up: it follows the finger
        //    and turns upright. Releasing it mid-swipe throws it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(sandbox) {
                    // Watches without consuming, so it never competes with the drag below.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        val quick = up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                        val still = (up.position - down.position).getDistance() < viewConfiguration.touchSlop
                        if (quick && still) sandbox.itemAt(up.position)?.let { currentOnItemClick(it.spec) }
                    }
                }
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
                                val hasPeers = currentSharing?.channel?.peers?.isNotEmpty() == true
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
                },
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
    }
}

private fun tiltMoved(tilt: TiltState, applied: Vec2, strength: Float, threshold: Float): Boolean {
    val dx = tilt.gx * strength - applied.x
    val dy = tilt.gy * strength - applied.y
    return dx * dx + dy * dy > threshold * threshold
}
