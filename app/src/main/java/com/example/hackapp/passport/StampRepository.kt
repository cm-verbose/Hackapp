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
     * reads and saves in one step and is safe to call again for the same Itinerary. Throws
     * [PassportUnavailableException] when the stored Passport cannot be read or written.
     */
    /**
     * Like [observeStamps], but says when the stored Passport cannot be read. A Passport that is
     * [PassportState.Unavailable] is never overwritten, so Stamps are not lost to a bad file.
     */
    fun observePassport(uid: String): Flow<PassportState> =
        observeStamps(uid).map<List<PassportStamp>, PassportState> { PassportState.Loaded(it) }

    suspend fun award(
        itinerary: Itinerary,
        resolver: StampIdentityResolver,
        awardedAtMillis: Long,
    ): StampAward
}

internal fun List<PassportStamp>.inPassportOrder(): List<PassportStamp> =
    sortedWith(compareByDescending<PassportStamp> { it.awardedAtMillis }.thenBy { it.name }.thenBy { it.key })

sealed interface PassportState {
    data class Loaded(val stamps: List<PassportStamp>) : PassportState
    data object Unavailable : PassportState
}

/** [StampRepository.award] could not read or save the Passport. Nothing was changed. */
class PassportUnavailableException(cause: Throwable) : Exception("The Passport cannot be read", cause)

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
