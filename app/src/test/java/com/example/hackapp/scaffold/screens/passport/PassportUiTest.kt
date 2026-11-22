package com.example.hackapp.scaffold.screens.passport

import com.example.hackapp.passport.PassportStamp
import com.example.hackapp.passport.PassportState
import com.example.hackapp.passport.StampKind
import com.example.hackapp.ui.theme.StopType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PassportUiTest {
    private val format = DateTimeFormatter.ISO_LOCAL_DATE

    private fun stamp(key: String, city: String = "mtl", district: String = "ville-marie") = PassportStamp(
        key = key, ownerUid = "u", cityId = city, kind = StampKind.Event, name = "Jazz sur le toit",
        date = LocalDate.of(2026, 11, 28), districtId = district, category = "music",
        itineraryId = "i", stopId = key, awardedAtMillis = 1L,
    )

    @Test
    fun `no state yet is loading and an unreadable Passport is unavailable`() {
        assertSame(PassportContent.Loading, passportContent(null, "mtl", emptyMap(), format))
        assertSame(PassportContent.Unavailable, passportContent(PassportState.Unavailable, "mtl", emptyMap(), format))
    }

    @Test
    fun `only the selected city is shown and its Districts are counted`() {
        val state = PassportState.Loaded(
            listOf(stamp("a"), stamp("b", district = "plateau-mont-royal"), stamp("c"), stamp("t", city = "tor")),
        )
        val ready = passportContent(state, "mtl", emptyMap(), format) as PassportContent.Ready
        assertEquals(listOf("a", "b", "c"), ready.stamps.map { it.key })
        assertEquals(2, ready.districtCount)
    }

    @Test
    fun `the spoken name has the Stamp name, District and date`() {
        val state = PassportState.Loaded(listOf(stamp("a")))
        val ui = (passportContent(state, "mtl", mapOf("ville-marie" to "Ville-Marie"), format) as PassportContent.Ready).stamps.single()
        assertEquals("JS", ui.initials)
        assertEquals("Jazz sur le toit, Ville-Marie, 2026-11-28", ui.description)
    }

    @Test
    fun `Stamp kinds map to their stop colours`() {
        assertEquals(StopType.Music, stampStopType(StampKind.Event, "music"))
        assertEquals(StopType.Bar, stampStopType(StampKind.Meal, "bar"))
        assertEquals(StopType.Meal, stampStopType(StampKind.Meal, "restaurant"))
    }

    @Test
    fun `tilt is stable and small`() {
        listOf("a", "mtl:place:123", "mtl:chain:boustan").forEach {
            assertEquals(stampTilt(it), stampTilt(it), 0f)
            assertTrue(stampTilt(it) in -20f..20f)
        }
    }
}
