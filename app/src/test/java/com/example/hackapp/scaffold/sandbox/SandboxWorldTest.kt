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
    fun `isListed is false once the key is gone`() {
        val world = world("a")
        val shape = world.items.single().physicalShape
        assertTrue(world.isListed(shape, listOf(item("a"))))
        assertFalse(world.isListed(shape, emptyList()))
    }
}
