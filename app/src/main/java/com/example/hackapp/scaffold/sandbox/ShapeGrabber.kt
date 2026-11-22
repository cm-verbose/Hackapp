package com.example.hackapp.scaffold.sandbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.example.hackapp.scaffold.sandbox.shape.PhysicalShape
import com.example.hackapp.scaffold.sandbox.shape.toMeters
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.World
import org.jbox2d.dynamics.joints.MouseJoint
import org.jbox2d.dynamics.joints.MouseJointDef
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lets the user pick up a shape and drag it around. Priorities while a shape is held:
 *
 * 1. **Movement**: the shape tracks the finger tightly. It doesn't spin and isn't pulled
 *    by gravity while held, so it feels firmly in hand.
 * 2. **Rotation**: after [ROTATE_DELAY_SECONDS] of holding, the shape smoothly turns upright
 *    relative to the screen so its text is readable.
 *
 * Dragging goes through a Box2D [MouseJoint] instead of teleporting the body, so the held
 * shape still collides with and pushes the other shapes.
 *
 * All methods must be called from the thread that steps the [world] (the main thread).
 */
class ShapeGrabber(private val world: World) {

    /** Fixture-less static body that the mouse joint is anchored to. */
    private val anchorBody: Body = world.createBody(BodyDef())
    private var joint: MouseJoint? = null

    /** Offset from the finger to the body's center, kept so the shape doesn't jump. */
    private val grabOffset = Vec2()

    /** How long the current shape has been held, in simulated seconds. */
    private var heldSeconds = 0f

    /** The body's gravity scale before it was picked up, restored on release. */
    private var savedGravityScale = 1f

    /** The shape currently held by the user, or `null`. */
    var grabbed by mutableStateOf<PhysicalShape?>(null)
        private set

    /**
     * Picks up the top-most shape under [positionPx] (screen pixels).
     * @return `true` if a shape was grabbed.
     */
    fun grab(shapes: List<PhysicalShape>, positionPx: Offset): Boolean {
        release()
        val point = positionPx.toWorld()
        // Later items are drawn on top, so search from the end.
        val hit = shapes.lastOrNull { it.body.containsPoint(point) } ?: return false
        val body = hit.body

        body.isAwake = true
        // Stop whatever the shape was doing so it settles into the hand immediately.
        body.linearVelocity = Vec2(0f, 0f)
        body.angularVelocity = 0f
        savedGravityScale = body.gravityScale
        body.gravityScale = 0f
        grabOffset.set(body.worldCenter).subLocal(point)

        val def = MouseJointDef().apply {
            bodyA = anchorBody
            bodyB = body
            // The joint pins the point on the body where the target starts. Starting at the
            // center means dragging never applies torque, so rotation is fully ours to control.
            target.set(body.worldCenter)
            maxForce = MAX_FORCE_PER_KG * body.mass
            frequencyHz = JOINT_FREQUENCY_HZ
            dampingRatio = JOINT_DAMPING_RATIO
        }
        joint = world.createJoint(def) as MouseJoint
        heldSeconds = 0f
        grabbed = hit
        return true
    }

    /** Moves the held shape toward the finger at [positionPx] (screen pixels). */
    fun moveTo(positionPx: Offset) {
        val joint = joint ?: return
        joint.setTarget(positionPx.toWorld().addLocal(grabOffset))
    }

    /**
     * Drops the held shape, if any. It keeps the velocity it was being dragged at, so a quick
     * swipe throws it.
     * @return the shape that was released, or `null` if nothing was held.
     */
    fun release(): PhysicalShape? {
        joint?.let { world.destroyJoint(it) }
        joint = null
        val released = grabbed
        released?.body?.let {
            it.gravityScale = savedGravityScale
            it.isAwake = true
        }
        grabbed = null
        return released
    }

    /**
     * Advances the held shape's rotation by [dt] seconds. Call before every world step.
     *
     * The shape never spins freely while held. Once it has been held for
     * [ROTATE_DELAY_SECONDS], its angle eases toward upright (angle 0, taking the short way
     * round). The angle is set directly rather than through velocity, so collisions and
     * damping can't stop it from turning.
     */
    fun update(dt: Float) {
        val body = grabbed?.body ?: return
        body.angularVelocity = 0f

        heldSeconds += dt
        if (heldSeconds < ROTATE_DELAY_SECONDS) return

        val diff = shortestAngle(-body.angle)
        if (abs(diff) < SNAP_EPSILON_RAD) {
            if (diff != 0f) body.setTransform(body.position, body.angle + diff)
            return
        }
        // Exponential ease: covers a fixed fraction of the remaining angle each step.
        val factor = (ROTATION_RATE * dt).coerceAtMost(1f)
        body.setTransform(body.position, body.angle + diff * factor)
    }

    private fun Offset.toWorld() = Vec2(x.toMeters(), y.toMeters())

    private fun Body.containsPoint(point: Vec2): Boolean {
        var fixture = fixtureList
        while (fixture != null) {
            if (fixture.testPoint(point)) return true
            fixture = fixture.next
        }
        return false
    }

    /** Wraps an angle difference into (-PI, PI] so the shape turns the short way round. */
    private fun shortestAngle(angle: Float): Float = atan2(sin(angle), cos(angle))

    private companion object {
        /** How long a shape must be held before it starts turning upright. */
        const val ROTATE_DELAY_SECONDS = 0.25f

        /** How quickly the shape eases upright (1/s); ~95% of the way in about 0.3s. */
        const val ROTATION_RATE = 10f

        /** Below this remaining angle (~0.5°) the shape snaps exactly upright. */
        const val SNAP_EPSILON_RAD = 0.01f

        /** Joint stiffness. Higher follows the finger more tightly (must stay < step rate / 2). */
        const val JOINT_FREQUENCY_HZ = 12f

        /** 1.0 = critically damped: reaches the finger without overshooting or wobbling. */
        const val JOINT_DAMPING_RATIO = 1f

        /** Pulling strength relative to body mass, so big shapes feel as responsive as small. */
        const val MAX_FORCE_PER_KG = 3000f
    }
}
