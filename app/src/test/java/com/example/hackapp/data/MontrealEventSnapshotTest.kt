package com.example.hackapp.data

import com.example.hackapp.data.repository.EventQuery
import com.example.hackapp.data.repository.SnapshotEventRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.OffsetDateTime

/** Validates the real bundled snapshot. Gradle runs unit tests from the app module directory. */
class MontrealEventSnapshotTest {
    private val assets = File("src/main/assets/citypacks/mtl")
    private val repository = SnapshotEventRepository("mtl") {
        File(assets, "events.json").readText()
    }

    private fun boroughIds(): Set<String> =
        Json.parseToJsonElement(File(assets, "districts.json").readText())
            .jsonObject.getValue("districts").jsonArray
            .map { it.jsonObject }
            .filter { it["groupId"]?.jsonPrimitive?.contentOrNullSafe() != "linked" }
            .map { it.getValue("id").jsonPrimitive.content }
            .toSet()

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        if (this is kotlinx.serialization.json.JsonNull) null else content

    @Test
    fun `every event can be planned`() = runBlocking {
        val events = repository.search(EventQuery(includeUnavailable = true))
        val boroughs = boroughIds()
        assertTrue(events.size > 1000)
        assertEquals(events.size, events.map { it.id }.toSet().size)
        events.forEach { event ->
            assertTrue(event.id, event.districtId in boroughs)
            assertNotNull(event.id, event.location.latitude)
            assertNotNull(event.id, event.location.longitude)
            event.schedule.endDate?.let { end ->
                assertTrue(event.id, OffsetDateTime.parse(end) > OffsetDateTime.parse(event.schedule.startDate))
            }
        }
    }

    @Test
    fun `demo evening has events in the main districts`() = runBlocking {
        val counts = repository.countByDistrict(
            EventQuery(
                startsAtOrAfter = OffsetDateTime.parse("2026-11-28T18:00:00-05:00"),
                startsBefore = OffsetDateTime.parse("2026-11-29T02:00:00-05:00"),
            ),
        )
        assertTrue((counts["ville-marie"] ?: 0) > 0)
        assertTrue((counts["plateau-mont-royal"] ?: 0) > 0)
    }
}
