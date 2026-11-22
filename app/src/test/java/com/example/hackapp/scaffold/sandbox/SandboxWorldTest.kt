package com.example.hackapp.scaffold.sandbox

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jbox2d.common.Vec2
import kotlin.math.sqrt

class SandboxWorldTest {
    private fun item(key: String) = SandboxItem(key, Color.Red, CircleShape, 72.dp, 72.dp)

    private fun world(vararg keys: String) = SandboxWorld(1080f, 1920f, Density(3f)).apply {
        populate(keys.map(::item), 1f, 1f)
    }

    private fun SandboxWorld.keys() = items.map { it.spec.key }

    @Test
    fun `sync keeps the body of a Stamp that stays`() {
        val world = world("a", "b")
        val body = world.items.first { it.spec.key == "a" }.physicalShape.body

        world.sync(listOf(item("new"), item("a"), item("b")))

        assertEquals(setOf("a", "b", "new"), world.keys().toSet())
        assertSame(body, world.items.first { it.spec.key == "a" }.physicalShape.body)
    }

    @Test
    fun `repeating the same list creates no duplicate`() {
        val world = world("a", "b")
        repeat(3) { world.sync(listOf(item("a"), item("b"))) }
        assertEquals(2, world.items.size)
    }

    @Test
    fun `a removed Stamp leaves the world`() {
        val world = world("a", "b")
        world.sync(listOf(item("b")))
        assertEquals(listOf<Any>("b"), world.keys())
    }

    @Test
    fun `new Stamps do not start on top of each other`() {
        val world = world("a", "b", "c", "d")
        val positions = world.items.map { it.physicalShape.body.position.clone() }
        positions.forEachIndexed { i, p ->
            positions.drop(i + 1).forEach { q -> assertTrue(p.sub(q).length() * 100f >= 216f) }
        }
    }

    @Test
    fun `capacity counts the cells that fit`() {
        assertEquals(4 * 8, sandboxCapacity(1080f, 1920f, 216f, 3f))
        assertEquals(0, sandboxCapacity(100f, 1920f, 216f, 3f))
    }

    @Test
    fun `the corner control takes one cell from the capacity`() {
        assertEquals(31, sandboxCapacity(1080f, 1920f, 216f, 3f, keepOutPx = 204f))
    }

    @Test
    fun `no Stamp can rest under the corner control`() {
        val keepOut = 204f
        val world = SandboxWorld(1080f, 1920f, Density(3f), keepOut).apply {
            populate((1..20).map { item("s$it") }, 0.05f, 0.1f)
        }
        world.world.gravity = Vec2(20f, 28f)
        repeat(600) { world.world.step(1f / 60f, 8, 3) }

        val radius = 108f
        world.items.forEach { stamp ->
            val x = stamp.physicalShape.body.position.x * 100f
            val y = stamp.physicalShape.body.position.y * 100f
            val dx = maxOf(1080f - keepOut - x, 0f)
            val dy = maxOf(1920f - keepOut - y, 0f)
            assertTrue("${stamp.spec.key} is under the control", sqrt(dx * dx + dy * dy) >= radius - 3f)
        }
    }

    @Test
    fun `isListed is false once the key is gone`() {
        val world = world("a")
        val shape = world.items.single().physicalShape
        assertTrue(world.isListed(shape, listOf(item("a"))))
        assertFalse(world.isListed(shape, emptyList()))
    }
}
