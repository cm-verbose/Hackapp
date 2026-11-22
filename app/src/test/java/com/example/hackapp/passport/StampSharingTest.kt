package com.example.hackapp.passport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StampSharingTest {
    private val stamp = PassportStamp(
        key = "mtl:place:42", ownerUid = "owner", cityId = "mtl", kind = StampKind.Meal, name = "Bistro du coin",
        date = LocalDate.of(2026, 11, 28), districtId = "ville-marie", category = "restaurant",
        itineraryId = "it", stopId = "s", awardedAtMillis = 1L, shapeSeed = 7,
    )

    @Test
    fun `a shared Stamp keeps what the keepsake shows and leaves the owner behind`() {
        val payload = stamp.toSharedPayload(photo = "abc")
        val shared = decodeSharedStamp(payload)!!
        assertEquals("Bistro du coin", shared.name)
        assertEquals("2026-11-28", shared.date)
        assertEquals(7, shared.shapeSeed)
        assertEquals("abc", shared.photo)
        assertEquals(false, payload.contains("owner"))
    }

    @Test
    fun `anything else is refused`() {
        assertNull(decodeSharedStamp("{\"itinerary\":{}}"))
        assertNull(decodeSharedStamp("not json"))
    }
}
