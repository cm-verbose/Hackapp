package com.example.hackapp.passport

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.hackapp.itinerary.Itinerary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Stores a person's Passport. Screens depend on this interface, so Firestore can replace it. */
interface StampRepository {
    /** All of this person's Stamps, most recently awarded first, then by name. */
    fun observeStamps(uid: String): Flow<List<PassportStamp>>

    /**
     * Awards the Stamps of [itinerary] to its owner and returns the result for every Stop. It
     * reads and saves in one step and is safe to call again for the same Itinerary.
     */
    suspend fun award(
        itinerary: Itinerary,
        resolver: StampIdentityResolver,
        awardedAtMillis: Long,
    ): StampAward
}

internal fun List<PassportStamp>.inPassportOrder(): List<PassportStamp> =
    sortedWith(compareByDescending<PassportStamp> { it.awardedAtMillis }.thenBy { it.name }.thenBy { it.key })

/** Keeps Stamps for this process only. Used by previews and tests. */
class InMemoryStampRepository : StampRepository {
    private val items = MutableStateFlow<Map<String, PassportStamp>>(emptyMap())

    override fun observeStamps(uid: String): Flow<List<PassportStamp>> = items.map { all ->
        all.values.filter { it.ownerUid == uid }.inPassportOrder()
    }

    override suspend fun award(itinerary: Itinerary, resolver: StampIdentityResolver, awardedAtMillis: Long): StampAward {
        lateinit var award: StampAward
        while (true) {
            val before = items.value
            award = awardStamps(itinerary, before.values, resolver, awardedAtMillis)
            if (items.compareAndSet(before, before + award.created.associateBy { ownerKey(it) })) break
        }
        return award
    }

    private fun ownerKey(stamp: PassportStamp) = "${stamp.ownerUid}|${stamp.key}"
}

val LocalStampRepository = staticCompositionLocalOf<StampRepository> { InMemoryStampRepository() }
