package com.example.hackapp.data

import com.example.hackapp.data.repository.EventQuery
import com.example.hackapp.data.repository.SnapshotEventRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.OffsetDateTime

class SnapshotEventRepositoryTest {

    private fun event(id: String, start: String, district: String?, status: String = "scheduled") = """
        {"id":"$id","title":"$id","type":"music","status":"$status",
         ${district?.let { "\"districtId\":\"$it\"," } ?: ""}
         "location":{"city":"Montréal"},
         "schedule":{"startDate":"$start","timezone":"America/Toronto"},
         "cost":{"status":"unknown"}}
    """

    private val snapshot = """
        {"cityId":"mtl","generatedAt":"2026-11-21T00:00:00Z","events":[
          ${event("late", "2026-11-28T21:00:00-05:00", "ville-marie")},
          ${event("early", "2026-11-28T18:00:00-05:00", "ville-marie")},
          ${event("plateau", "2026-11-28T19:00:00-05:00", "plateau-mont-royal")},
          ${event("cancelled", "2026-11-28T19:00:00-05:00", "ville-marie", "cancelled")},
          ${event("date-only", "2026-11-28", "ville-marie")},
          ${event("no-district", "2026-11-28T19:00:00-05:00", null)}
        ]}
    """

    private val repository = SnapshotEventRepository("mtl") { snapshot }

    @Test
    fun `search orders by start and hides cancelled and untimed events`() = runBlocking {
        assertEquals(
            listOf("early", "plateau", "no-district", "late"),
            repository.search().map { it.id },
        )
    }

    @Test
    fun `search filters by district and start window`() = runBlocking {
        val results = repository.search(
            EventQuery(
                districtIds = setOf("ville-marie"),
                startsAtOrAfter = OffsetDateTime.parse("2026-11-28T18:00:00-05:00"),
                startsBefore = OffsetDateTime.parse("2026-11-28T21:00:00-05:00"),
            ),
        )
        assertEquals(listOf("early"), results.map { it.id })
    }

    @Test
    fun `countByDistrict counts only available placed events`() = runBlocking {
        assertEquals(
            mapOf("ville-marie" to 2, "plateau-mont-royal" to 1),
            repository.countByDistrict(),
        )
    }

    @Test
    fun `getEvent returns null for unknown id`() = runBlocking {
        assertEquals("late", repository.getEvent("late")?.id)
        assertNull(repository.getEvent("missing"))
    }

    @Test
    fun `snapshot for another city is rejected`() {
        val wrongCity = SnapshotEventRepository("tor") { snapshot }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { wrongCity.search() }
        }
    }
}
