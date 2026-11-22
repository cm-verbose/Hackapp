package com.example.hackapp.passport

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.Nov28
import com.example.hackapp.itinerary.StopStatus
import com.example.hackapp.itinerary.at
import com.example.hackapp.itinerary.night
import com.example.hackapp.itinerary.stop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StampRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("stamps.preferences_pb") }
    }
    private val resolver = StampIdentityResolver { stop, _ -> StampIdentity("event:${stop.sourceId}", "music") }
    private val itinerary = night(phase = ItineraryPhase.Completed, stops = listOf(
        stop("a", at(Nov28, 19), at(Nov28, 20), status = StopStatus.Confirmed),
        stop("b", at(Nov28, 21), at(Nov28, 22), status = StopStatus.Skipped),
    ))

    @After
    fun closeStore() = scope.cancel()

    @Test
    fun `a Stamp survives encoding`() {
        val stamp = awardStamps(itinerary, emptyList(), resolver, 5).created.single()
        assertEquals(stamp, StampCodec.decode(StampCodec.encode(stamp)))
        assertNull(StampCodec.decode("not json"))
        assertNull(StampCodec.decode(StampCodec.encode(stamp).replace("2026-11-28", "nope")))
    }

    @Test
    fun `DataStore awarding is idempotent and keeps Stamps after a restart`() = runBlocking {
        val repository = DataStoreStampRepository(store)
        val first = repository.award(itinerary, resolver, 10)
        val retry = repository.award(itinerary, resolver, 99)
        assertEquals(1, first.created.size)
        assertTrue(retry.created.isEmpty())
        assertEquals(first.outcomes, retry.outcomes)
        val saved = DataStoreStampRepository(store).observeStamps(itinerary.ownerUid).first()
        assertEquals(first.created, saved)
        assertEquals(10, saved.single().awardedAtMillis)
    }

    @Test
    fun `Stamps belong to the signed-in user`() = runBlocking {
        val repository = DataStoreStampRepository(store)
        repository.award(itinerary, resolver, 10)
        assertTrue(repository.observeStamps("other").first().isEmpty())
        val other = repository.award(itinerary.copy(ownerUid = "other"), resolver, 11)
        assertEquals(1, other.created.size)
        assertEquals(1, repository.observeStamps("other").first().size)
        assertEquals(1, repository.observeStamps(itinerary.ownerUid).first().size)
    }

    @Test
    fun `in-memory repository follows the same rules`() = runBlocking {
        val repository = InMemoryStampRepository()
        repository.award(itinerary, resolver, 10)
        val retry = repository.award(itinerary, resolver, 11)
        assertTrue(retry.created.isEmpty())
        assertEquals(1, repository.observeStamps(itinerary.ownerUid).first().size)
        assertTrue(repository.observeStamps("other").first().isEmpty())
    }

    @Test
    fun `an existing unreadable record is never overwritten`() = runBlocking {
        val repository = DataStoreStampRepository(store)
        val candidate = awardStamps(itinerary, emptyList(), resolver, 5).created.single()
        val key = androidx.datastore.preferences.core.stringPreferencesKey("stamp_${candidate.ownerUid.length}_${candidate.ownerUid}_${candidate.key}")
        store.edit { it[key] = "garbage" }
        val result = repository.award(itinerary, resolver, 10)
        assertTrue(result.created.isEmpty())
        assertTrue(result.resultFor("a") is StopStampResult.AlreadyInPassport)
        assertEquals("garbage", store.data.first()[key])
    }

    @Test
    fun `a corrupt store reports Unavailable and is left untouched`() = runBlocking {
        val file = folder.root.resolve("bad.preferences_pb")
        val bytes = ByteArray(64) { (it * 7 + 3).toByte() }
        file.writeBytes(bytes)
        val badStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val repository = DataStoreStampRepository(badStore)
        assertEquals(PassportState.Unavailable, repository.observePassport(itinerary.ownerUid).first())
        assertTrue(repository.observeStamps(itinerary.ownerUid).first().isEmpty())
        try {
            repository.award(itinerary, resolver, 10)
            org.junit.Assert.fail("expected PassportUnavailableException")
        } catch (_: PassportUnavailableException) {
        }
        assertTrue(bytes.contentEquals(file.readBytes()))
    }
}
