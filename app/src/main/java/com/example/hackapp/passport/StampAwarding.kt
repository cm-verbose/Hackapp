package com.example.hackapp.passport

import com.example.hackapp.data.model.Place
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.itinerary.StopStatus
import java.text.Normalizer

/** What a Stop is for Stamp purposes. [key] is shared by everything that counts as one. */
data class StampIdentity(val key: String, val category: String? = null)

/** Decides which Stamp a Stop would earn. Replaceable, so chain rules can improve. */
fun interface StampIdentityResolver {
    fun identify(stop: Stop, cityId: String): StampIdentity
}

/** What a Stop got when Stamps were awarded. The End of day page shows this per Stop. */
sealed interface StopStampResult {
    /** The Stop earned this Stamp, on this run or on an earlier run of the same Itinerary. */
    data class NewStamp(val stamp: PassportStamp) : StopStampResult

    /** The Passport already held it from another Stop, location or day. */
    data class AlreadyInPassport(val stamp: PassportStamp) : StopStampResult

    /** Planned or skipped Stops earn nothing. */
    data object NoStamp : StopStampResult
}

data class StopStampOutcome(val stopId: String, val result: StopStampResult)

data class StampAward(
    /** One per Stop of the Itinerary, in its order. */
    val outcomes: List<StopStampOutcome>,
    /** Stamps that did not exist before this run and must be saved. Empty on a retry. */
    val created: List<PassportStamp>,
) {
    fun resultFor(stopId: String): StopStampResult? = outcomes.firstOrNull { it.stopId == stopId }?.result
}

/**
 * Works out the Stamps an Itinerary earns, given the Passport's current Stamps.
 *
 * Only Confirmed Stops count. Stops are taken in visit time order, so for a chain the first
 * location visited earns the Stamp. Running it again with the Stamps it created returns the same
 * outcomes and creates nothing, so a crash or retry cannot duplicate or demote a Stamp.
 */
fun awardStamps(
    itinerary: Itinerary,
    existing: Collection<PassportStamp>,
    resolver: StampIdentityResolver,
    awardedAtMillis: Long,
): StampAward {
    val held = existing.filter { it.ownerUid == itinerary.ownerUid }.associateByTo(mutableMapOf()) { it.key }
    val resultByStop = mutableMapOf<String, StopStampResult>()
    val created = mutableListOf<PassportStamp>()
    itinerary.stops
        .filter { it.status == StopStatus.Confirmed }
        .sortedBy { it.start.toInstant() }
        .forEach { stop ->
            val identity = resolver.identify(stop, itinerary.cityId)
            val key = "${itinerary.cityId}:${identity.key}"
            val stamp = held[key]
            resultByStop[stop.id] = when {
                stamp == null -> {
                    val fresh = PassportStamp(
                        key = key,
                        ownerUid = itinerary.ownerUid,
                        cityId = itinerary.cityId,
                        kind = if (stop.kind == StopKind.Event) StampKind.Event else StampKind.Meal,
                        name = stop.name,
                        date = itinerary.date,
                        districtId = stop.districtId,
                        category = identity.category,
                        itineraryId = itinerary.id,
                        stopId = stop.id,
                        awardedAtMillis = awardedAtMillis,
                    )
                    held[key] = fresh
                    created += fresh
                    StopStampResult.NewStamp(fresh)
                }
                stamp.itineraryId == itinerary.id && stamp.stopId == stop.id -> StopStampResult.NewStamp(stamp)
                else -> StopStampResult.AlreadyInPassport(stamp)
            }
        }
    return StampAward(
        outcomes = itinerary.stops.map { StopStampOutcome(it.id, resultByStop[it.id] ?: StopStampResult.NoStamp) },
        created = created,
    )
}

/**
 * Event Stops are identified by Event id. Meal Stops by Place id, or by a normalised name when
 * the Place is a chain. The Place data has only an `isChain` flag and no brand id, so the name is
 * the best chain identity available. A Place the lookup cannot find counts as its own Place.
 */
class PlaceStampIdentityResolver(
    private val placeById: (String) -> Place?,
    private val eventType: (String) -> String? = { null },
) : StampIdentityResolver {
    override fun identify(stop: Stop, cityId: String): StampIdentity = when (stop.kind) {
        StopKind.Event -> StampIdentity("event:${stop.sourceId}", eventType(stop.sourceId)?.lowercase())
        StopKind.Meal -> {
            val place = placeById(stop.sourceId)
            val category = place?.venueType?.name?.lowercase()
            val brand = place?.takeIf { it.isChain }?.let { chainKey(it.name) }?.takeIf { it.isNotEmpty() }
            if (brand != null) StampIdentity("chain:$brand", category)
            else StampIdentity("place:${stop.sourceId}", category)
        }
    }
}

private val diacritics = Regex("\\p{Mn}+")
private val parenthetical = Regex("\\(.*?\\)")
private val notAlphanumeric = Regex("[^a-z0-9]+")

/**
 * "Tim Hortons - Rue Saint-Denis" and "Tim Hortons (Berri)" both become "timhortons". Branch
 * labels after " - " or in brackets are dropped. A branch written another way would not match.
 */
internal fun chainKey(name: String): String {
    val brand = name.substringBefore(" - ").substringBefore(" – ")
    val plain = Normalizer.normalize(brand.replace(parenthetical, " "), Normalizer.Form.NFD)
        .replace(diacritics, "")
        .lowercase()
    return plain.replace(notAlphanumeric, "")
}
