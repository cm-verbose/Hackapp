package com.example.hackapp.scaffold.sandbox

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import kotlin.math.abs
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
    fun `capacity counts what fits`() {
        val stamps = List(40) { 216f to 216f }
        assertEquals(32, sandboxFitCount(stamps, 1080f, 1920f, 3f))
        assertEquals(0, sandboxFitCount(stamps, 100f, 1920f, 3f))
    }

    @Test
    fun `the corner control takes room from the capacity`() {
        val stamps = List(40) { 216f to 216f }
        assertEquals(31, sandboxFitCount(stamps, 1080f, 1920f, 3f, keepOutPx = 204f))
    }

    @Test
    fun `mixed sizes never start overlapping or under the control`() {
        val sizes = listOf(540f to 540f, 480f to 480f, 300f to 300f, 450f to 240f, 480f to 240f, 330f to 330f)
        val placed = ArrayList<PlacedBox>()
        repeat(12) { index ->
            val (w, h) = sizes[index % sizes.size]
            firstFreeSpot(w, h, placed, 1080f, 1920f, 3f, keepOutPx = 204f)?.let { spot ->
                assertFalse(placed.any { abs(it.x - spot.x) < it.halfWidth + spot.halfWidth && abs(it.y - spot.y) < it.halfHeight + spot.halfHeight })
                assertFalse(spot.x + spot.halfWidth > 876f && spot.y + spot.halfHeight > 1716f)
                placed += spot
            }
        }
        assertTrue(placed.size >= 6)
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

class SandboxScaleTest {
    private fun item(key: String) = SandboxItem(key, Color.Red, CircleShape, 110.dp, 110.dp)

    private fun SandboxWorld.settleScale() = repeat(60) {
        stepScale()
        world.step(1f / 60f, 8, 3)
    }

    private fun SandboxWorld.assertInside(width: Float, height: Float) = items.forEach {
        val x = it.physicalShape.body.position.x * 100f
        val y = it.physicalShape.body.position.y * 100f
        assertTrue("${it.spec.key} left the field at $x, $y", x in 0f..width && y in 0f..height)
    }

    @Test
    fun `the first Stamps stay at full size and many Stamps stop at half`() {
        val shape = 110f to 110f
        assertEquals(SandboxLayout(1f, 3), sandboxLayout(List(3) { shape }, 380f, 557f, 1f, 68f))
        val crowded = sandboxLayout(List(200) { shape }, 380f, 557f, 1f, 68f)
        assertEquals(MIN_ITEM_SCALE, crowded.scale)
        assertTrue(crowded.count in 1 until 200)
    }

    @Test
    fun `a bigger collection never needs a bigger scale`() {
        val shapes = listOf(180f to 180f, 160f to 160f, 100f to 100f, 150f to 80f, 160f to 80f, 110f to 110f)
        val sizes = List(40) { shapes[(it * it + 3 * it) % shapes.size] }
        // The list is newest first, so a new Stamp joins at its start.
        val scales = (1..sizes.size).map { sandboxLayout(sizes.takeLast(it), 380f, 557f, 1f, 68f).scale }
        scales.zipWithNext().forEach { (before, after) -> assertTrue(after <= before) }
    }

    @Test
    fun `shrinking keeps positions and shrinks the collision shape`() {
        val world = SandboxWorld(380f, 557f, Density(1f), 68f).apply {
            populate(listOf(item("a"), item("b")), 1f, 1f)
            this.world.gravity = Vec2(0f, 0f)
        }
        val before = world.items.map { it.physicalShape.body.position.clone() }

        world.sync(listOf(item("a"), item("b")), 0.5f)
        world.settleScale()

        assertEquals(0.5f, world.itemScale, 0f)
        world.items.forEachIndexed { index, it ->
            assertEquals(before[index].x, it.physicalShape.body.position.x, 0.01f)
            assertEquals(before[index].y, it.physicalShape.body.position.y, 0.01f)
            assertEquals(0.275f, it.physicalShape.body.fixtureList.shape.m_radius, 0.001f)
        }
    }

    @Test
    fun `a polygon outline scales with the Stamp`() {
        val box = SandboxItem("box", Color.Red, RoundedCornerShape(16.dp), 160.dp, 80.dp)
        val world = SandboxWorld(380f, 557f, Density(1f), 68f).apply {
            populate(listOf(box), 1f, 1f)
            this.world.gravity = Vec2(0f, 0f)
        }

        world.sync(listOf(box), 0.5f)
        world.settleScale()

        val polygon = world.items.single().physicalShape.body.fixtureList.shape as PolygonShape
        val vertices = List(polygon.vertexCount) { polygon.getVertex(it) }
        assertEquals(0.4f, vertices.maxOf { it.x }, 0.001f)
        assertEquals(0.2f, vertices.maxOf { it.y }, 0.001f)
    }

    @Test
    fun `a new Stamp waits for the shrink and then lands clear`() {
        val world = SandboxWorld(380f, 557f, Density(1f), 68f).apply {
            populate(List(6) { item("s$it") }, 1f, 1f)
            this.world.gravity = Vec2(0f, 0f)
        }
        world.sync(List(7) { item("s$it") }, 0.7f)
        assertEquals(6, world.items.size)

        world.settleScale()

        assertEquals(7, world.items.size)
        val added = world.items.last().physicalShape.body.position
        world.items.dropLast(1).forEach {
            assertTrue(it.physicalShape.body.position.sub(added).length() * 100f >= 77f - 1f)
        }
    }

    @Test
    fun `growing a tight pile stays inside the walls and stable`() {
        val world = SandboxWorld(380f, 557f, Density(1f), 68f).apply {
            populate(List(20) { item("s$it") }, 1f, 1f, scale = 0.5f)
            this.world.gravity = Vec2(10f, 28f)
        }
        repeat(120) { world.world.step(1f / 60f, 8, 3) }

        world.sync(List(20) { item("s$it") }, 0.8f)
        repeat(180) {
            world.stepScale()
            world.world.step(1f / 60f, 8, 3)
            world.items.forEach { stamp ->
                assertTrue(stamp.physicalShape.body.linearVelocity.length() < 60f)
            }
        }
        world.assertInside(380f, 557f)
    }
}
