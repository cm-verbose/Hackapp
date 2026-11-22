package com.example.hackapp.passport

import com.example.hackapp.data.model.Place
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryPhase
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.itinerary.StopStatus
import java.text.Normalizer
import java.util.Locale

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
 * Only Confirmed Stops of a Completed Itinerary count. Any other phase earns nothing.
 *
 * A Stop that already holds a Stamp from this Itinerary (same `itineraryId` and `stopId`) is
 * answered from it before the identity is resolved, so a retry never depends on the Place lookup.
 * The lookup must still include non-ACTIVE Places, or a first run could not identify a Place that
 * closed later. Only Confirmed Stops count. Stops are taken in visit time order, so for a chain the first
 * location visited earns the Stamp. Running it again with the Stamps it created returns the same
 * outcomes and creates nothing, so a crash or retry cannot duplicate or demote a Stamp.
 */
fun awardStamps(
    itinerary: Itinerary,
    existing: Collection<PassportStamp>,
    resolver: StampIdentityResolver,
    awardedAtMillis: Long,
): StampAward {
    if (itinerary.phase != ItineraryPhase.Completed) {
        return StampAward(itinerary.stops.map { StopStampOutcome(it.id, StopStampResult.NoStamp) }, emptyList())
    }
    val mine = existing.filter { it.ownerUid == itinerary.ownerUid }
    val held = mine.associateByTo(mutableMapOf()) { it.key }
    val resultByStop = mutableMapOf<String, StopStampResult>()
    val created = mutableListOf<PassportStamp>()
    itinerary.stops
        .filter { it.status == StopStatus.Confirmed }
        .sortedBy { it.start.toInstant() }
        .forEach { stop ->
            val own = mine.firstOrNull { it.itineraryId == itinerary.id && it.stopId == stop.id }
            if (own != null) {
                resultByStop[stop.id] = StopStampResult.NewStamp(own)
                return@forEach
            }
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
                else -> StopStampResult.AlreadyInPassport(stamp)
            }
        }
    return StampAward(
        outcomes = itinerary.stops.map { StopStampOutcome(it.id, resultByStop[it.id] ?: StopStampResult.NoStamp) },
        created = created,
    )
}

/**
 * One Event earns one Stamp ever, whatever the showing. The Event id differs for each showing, so
 * an Event Stop is identified by its normalised name and its position rounded to 3 decimals
 * (about 100 m). Meal Stops are identified by the Place's stable `city_id`, or by the brand when
 * the Place is flagged `isChain` and its name starts with an ALLOW brand of the ETL's
 * `config/reference.yaml` ([ChainBrands]). Places with `is_chain = 0` are never merged, whatever
 * their name. A Place the lookup cannot find counts as its own Place, keyed by the Stop's id.
 *
 * [placeById] receives `Stop.sourceId` and must also return non-ACTIVE Places, so a Stop can still
 * be identified after the Place closed.
 */
class PlaceStampIdentityResolver(
    private val placeById: (String) -> Place?,
    private val eventType: (String) -> String? = { null },
) : StampIdentityResolver {
    override fun identify(stop: Stop, cityId: String): StampIdentity = when (stop.kind) {
        StopKind.Event -> StampIdentity(eventKey(stop), eventType(stop.sourceId)?.lowercase())
        StopKind.Meal -> {
            val place = placeById(stop.sourceId)
            val category = place?.venueType?.name?.lowercase()
            val brand = place?.takeIf { it.isChain }?.let { ChainBrands.brandOf(it.name) }
            if (brand != null) StampIdentity("chain:$brand", category)
            else StampIdentity("place:${place?.cityId ?: stop.sourceId}", category)
        }
    }

    private fun eventKey(stop: Stop): String {
        val lat = "%.3f".format(Locale.ROOT, stop.latitude)
        val lng = "%.3f".format(Locale.ROOT, stop.longitude)
        return "event:${ChainBrands.normalise(stop.name)}:$lat:$lng"
    }
}

/**
 * The ALLOW brands of `etl/config/reference.yaml` (`brands`, decision ALLOW), as normalised
 * keys with their aliases. "Marius" is left out because the ETL matches it by OSM brand tag only
 * ("Marius et Fanny" is unrelated). Update this list with the YAML.
 */
internal object ChainBrands {
    private val keys = listOf(
        "labelleprovince", "lafleur", "cafedepot", "allomoncoco", "poutineville", "fritealors",
        "bennyco", "loeufrier", "lacage", "scores", "pacini", "cultures", "boustan", "jacklecoq",
    )

    /** The brand key when [name] starts with an ALLOW brand, otherwise null. */
    fun brandOf(name: String): String? {
        val key = normalise(name)
        return keys.firstOrNull { key.startsWith(it) }
    }

    private val diacritics = Regex("\\p{Mn}+")
    private val parenthetical = Regex("\\(.*?\\)")
    private val leadingRestaurant = Regex("^restaurants?\\s+")
    private val notAlphanumeric = Regex("[^a-z0-9]+")

    /**
     * Lower case, no accents, "oe" for the ligature, no branch label after " - " or in brackets,
     * no leading "restaurant(s) ", and only letters and digits.
     */
    fun normalise(name: String): String {
        val brand = name.substringBefore(" - ").substringBefore(" – ")
        val plain = Normalizer.normalize(brand.replace(parenthetical, " "), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .lowercase()
            .replace("œ", "oe")
            .trim()
            .replace(leadingRestaurant, "")
        return plain.replace(notAlphanumeric, "")
    }
}
