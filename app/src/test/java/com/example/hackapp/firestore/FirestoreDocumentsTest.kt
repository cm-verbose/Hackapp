package com.example.hackapp.firestore

import com.example.hackapp.itinerary.ShowtimeKey
import com.example.hackapp.itinerary.TicketPriceSummary
import com.example.hackapp.itinerary.UID
import com.example.hackapp.itinerary.night
import com.example.hackapp.profile.AgeRange
import com.example.hackapp.profile.AudiencePreference
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.Interest
import com.example.hackapp.profile.Profile
import com.example.hackapp.profile.VisitorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirestoreDocumentsTest {
    private val profile = Profile(
        visitorType = VisitorType.Visitor,
        name = "Camille",
        email = "camille@exemple.ca",
        ageRange = AgeRange.From25To34,
        districtOfStayId = "district-a",
        lunchBudget = BudgetBracket.Under20,
        dinnerBudget = BudgetBracket.Under50,
        eventBudget = BudgetBracket.From100,
        lateNightBudget = null,
        audience = setOf(AudiencePreference.FamilyFriendly),
        interests = setOf(Interest.Music),
        cuisineGroups = setOf("group-a"),
    )

    @Test
    fun `profile round trips through its document`() {
        val doc = FirestoreDocuments.profileDoc(UID, profile, 5)
        assertEquals(profile, FirestoreDocuments.profileFrom(UID, doc))
        assertEquals(FirestoreDocuments.SCHEMA_VERSION, doc["schemaVersion"])
    }

    @Test
    fun `profile that is missing, foreign, newer or unreadable counts as missing`() {
        val doc = FirestoreDocuments.profileDoc(UID, profile, 5)
        assertNull(FirestoreDocuments.profileFrom(UID, null))
        assertNull(FirestoreDocuments.profileFrom("someone-else", doc))
        assertNull(FirestoreDocuments.profileFrom(UID, doc - "schemaVersion"))
        assertNull(FirestoreDocuments.profileFrom(UID, doc + ("payload" to "not json")))
        assertNull(FirestoreDocuments.profileFrom(UID, doc + ("payload" to 3)))
    }

    @Test
    fun `newer schema throws for a profile and a single itinerary but is skipped in a list`() {
        val newer = FirestoreDocuments.profileDoc(UID, profile, 5) + ("schemaVersion" to 2)
        val thrown = runCatching { FirestoreDocuments.profileFrom(UID, newer) }.exceptionOrNull()
        assertEquals(2, (thrown as NewerSchemaException).version)
        val doc = FirestoreDocuments.itineraryDoc(night(id = "trip")) + ("schemaVersion" to 3)
        assertNull(FirestoreDocuments.itineraryFrom(UID, "trip", doc, strict = false))
        assertEquals(true, runCatching { FirestoreDocuments.itineraryFrom(UID, "trip", doc, strict = true) }.isFailure)
    }

    @Test
    fun `itinerary round trips and keeps its owner and id`() {
        val itinerary = night(id = "trip")
        val doc = FirestoreDocuments.itineraryDoc(itinerary)
        assertEquals(itinerary, FirestoreDocuments.itineraryFrom(UID, "trip", doc, strict = false))
        assertEquals(UID, doc["ownerUid"])
        assertEquals(itinerary.updatedAtMillis, doc["updatedAtMillis"])
    }

    @Test
    fun `itinerary stored under another uid or id is skipped`() {
        val doc = FirestoreDocuments.itineraryDoc(night(id = "trip"))
        assertNull(FirestoreDocuments.itineraryFrom("other", "trip", doc, strict = false))
        assertNull(FirestoreDocuments.itineraryFrom(UID, "other-id", doc, strict = false))
        assertNull(FirestoreDocuments.itineraryFrom(UID, "trip", doc + ("payload" to "{}"), strict = false))
        assertNull(FirestoreDocuments.itineraryFrom(UID, "trip", null, strict = false))
    }

    @Test
    fun `key id is a stable path-safe digest`() {
        val a = FirestoreDocuments.keyId(ShowtimeKey("film/x|salle|2026-11-28"))
        assertEquals(a, FirestoreDocuments.keyId(ShowtimeKey("film/x|salle|2026-11-28")))
        assertEquals(64, a.length)
        assertEquals(true, a.all { it in "0123456789abcdef" })
        assertNotEquals(a, FirestoreDocuments.keyId(ShowtimeKey("film/x|salle|2026-11-29")))
    }

    @Test
    fun `report document carries the owner and price`() {
        val doc = FirestoreDocuments.reportDoc(UID, 12.5, "ts")
        assertEquals(setOf("ownerUid", "priceCad", "updatedAt"), doc.keys)
        assertEquals(12.5, FirestoreDocuments.reportFrom(UID, doc)!!, 0.0)
    }

    @Test
    fun `report outside the range or of another owner is ignored`() {
        assertNull(FirestoreDocuments.reportFrom(UID, mapOf("ownerUid" to UID, "priceCad" to 500.01)))
        assertNull(FirestoreDocuments.reportFrom(UID, mapOf("ownerUid" to UID, "priceCad" to -1L)))
        assertNull(FirestoreDocuments.reportFrom(UID, mapOf("ownerUid" to "x", "priceCad" to 5L)))
        assertNull(FirestoreDocuments.reportFrom(UID, mapOf("ownerUid" to UID, "priceCad" to "5")))
        assertEquals(500.0, FirestoreDocuments.reportFrom(UID, mapOf("ownerUid" to UID, "priceCad" to 500L))!!, 0.0)
        assertEquals(0.0, FirestoreDocuments.reportFrom(UID, mapOf("ownerUid" to UID, "priceCad" to 0L))!!, 0.0)
    }

    @Test
    fun `summary needs a valid median and a positive count`() {
        assertEquals(
            TicketPriceSummary(17.5, 4),
            FirestoreDocuments.summaryFrom(mapOf("medianCad" to 17.5, "reportCount" to 4L)),
        )
        assertNull(FirestoreDocuments.summaryFrom(null))
        assertNull(FirestoreDocuments.summaryFrom(mapOf("medianCad" to 17.5)))
        assertNull(FirestoreDocuments.summaryFrom(mapOf("medianCad" to 900.0, "reportCount" to 3L)))
        assertNull(FirestoreDocuments.summaryFrom(mapOf("medianCad" to 10.0, "reportCount" to 0L)))
    }
}

class ProfileStateTest {
    private val failing = object : com.example.hackapp.profile.ProfileRepository {
        var error: Exception? = null
        override suspend fun getProfile(uid: String): Profile? = error?.let { throw it }
        override suspend fun saveProfile(uid: String, profile: Profile) = Unit
    }

    @Test
    fun `newer schema becomes UpdateRequired, other failures Failed, nothing Missing`() = kotlinx.coroutines.runBlocking {
        assertEquals(ProfileState.Missing, failing.loadProfileState("u"))
        failing.error = NewerSchemaException(4)
        assertEquals(ProfileState.UpdateRequired(4), failing.loadProfileState("u"))
        val boom = java.io.IOException("offline")
        failing.error = boom
        assertEquals(ProfileState.Failed(boom), failing.loadProfileState("u"))
    }
}

class StoredUpdatedAtTest {
    @Test
    fun `reads the stored version of any schema`() {
        assertEquals(7L, FirestoreDocuments.storedUpdatedAt(mapOf("schemaVersion" to 9, "updatedAtMillis" to 7L)))
        assertNull(FirestoreDocuments.storedUpdatedAt(null))
        assertNull(FirestoreDocuments.storedUpdatedAt(mapOf("updatedAtMillis" to "7")))
    }
}
