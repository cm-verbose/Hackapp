package com.example.hackapp.citypack

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class DistrictOriginTest {
    private val projection = MapProjection(kx = 0.7, minX = -52.0, minY = -46.0, scale = 2000.0)
    private val point = GeoPoint(lat = 45.5, lng = -73.6)
    private val anchor = projection.project(point)
    private val map = DistrictMapData(
        cityId = "test",
        viewBox = listOf(0f, 0f, 1000f, 1000f),
        districts = listOf(
            DistrictShape("a", "A", label = listOf(anchor.x, anchor.y), path = "M0,0 Z"),
            DistrictShape("broken", "Broken", label = listOf(1f), path = "M0,0 Z"),
        ),
        projection = projection,
    )

    @Test
    fun `a District resolves to its label anchor`() {
        val result = map.districtPoint("a")!!
        assertEquals(point.lat, result.lat, 0.001)
        assertEquals(point.lng, result.lng, 0.001)
    }

    @Test
    fun `missing inputs give no point`() {
        assertNull(map.districtPoint(null))
        assertNull(map.districtPoint(" "))
        assertNull(map.districtPoint("unknown"))
        assertNull(map.districtPoint("broken"))
        assertNull(map.copy(projection = null).districtPoint("a"))
        assertNull(map.copy(projection = projection.copy(scale = 0.0)).districtPoint("a"))
        assertNull(map.copy(projection = projection.copy(kx = 0.0)).districtPoint("a"))
    }

    @Test
    fun `the stay origin is the District of the Profile`() = runBlocking {
        val result = resolveStayOrigin({ "a" }, { map })
        assertEquals(point.lat, result!!.lat, 0.001)
    }

    @Test
    fun `a missing Profile or District gives null without reading the map`() = runBlocking {
        val noMap: suspend () -> DistrictMapData = { error("must not be read") }
        assertNull(resolveStayOrigin({ null }, noMap))
        assertNull(resolveStayOrigin({ "" }, noMap))
        assertNull(resolveStayOrigin({ "unknown" }, { map }))
    }

    @Test
    fun `unreadable data gives null`() = runBlocking {
        assertNull(resolveStayOrigin({ error("profile failed") }, { map }))
        assertNull(resolveStayOrigin({ "a" }, { error("map failed") }))
    }

    @Test
    fun `cancellation is not swallowed`() {
        assertThrows(CancellationException::class.java) {
            runBlocking { resolveStayOrigin({ throw CancellationException("stop") }, { map }) }
        }
    }

    @Test
    fun `every District of the Montreal pack resolves inside the city`() {
        val json = Json { ignoreUnknownKeys = true }
        val pack = json.decodeFromString<DistrictMapData>(
            File("src/main/assets/citypacks/mtl/districts.json").readText(Charsets.UTF_8),
        )
        pack.districts.forEach { district ->
            val result = pack.districtPoint(district.id)!!
            assertTrue("${district.id} lat ${result.lat}", result.lat in 45.38..45.72)
            assertTrue("${district.id} lng ${result.lng}", result.lng in -74.0..-73.45)
        }
    }
}
