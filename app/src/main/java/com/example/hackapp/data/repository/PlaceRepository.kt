package com.example.hackapp.data.repository

import com.example.hackapp.data.local.PlaceDao
import com.example.hackapp.data.local.PlaceWithTags
import com.example.hackapp.data.local.ReferenceDao
import com.example.hackapp.data.model.CuisineGroup
import com.example.hackapp.data.model.Dietary
import com.example.hackapp.data.model.Geo
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.data.model.LocalizedText
import com.example.hackapp.data.model.MealSlot
import com.example.hackapp.data.model.OpeningHours
import com.example.hackapp.data.model.Place
import com.example.hackapp.data.model.PriceBracket
import com.example.hackapp.data.model.PriceRange
import com.example.hackapp.data.model.PriceSource
import com.example.hackapp.data.model.VenueType
import java.time.ZonedDateTime

/** Everything the planner and the UI can ask for. All filters are optional. */
data class PlaceQuery(
    val center: LatLng,
    val radiusMeters: Double = 600.0,
    /** Null = every venue type. Use [PlaceRepository.venueTypesFor] to restrict by meal slot. */
    val venueTypes: Set<VenueType>? = null,
    /** Onboarding chip codes, e.g. "taste_of_mtl". Empty = no cuisine filter. */
    val cuisineGroups: Set<String> = emptySet(),
    /** Exclusive per-person max from the selected price bracket; null = any price. */
    val maxPrice: Int? = null,
    /** Only VEGETARIAN_ONLY / VEGAN_ONLY are used as hard filters (design §9). */
    val strictDietary: Dietary? = null,
    val includeChains: Boolean = true,
    /** If set, only places open at this time (places without hours are kept). */
    val openAt: ZonedDateTime? = null,
    val limit: Int = 50,
)

/**
 * Data-layer boundary for the app. The UI and the planner only see this interface, so the
 * Room implementation can later be swapped for a remote one (e.g. Supabase) without UI changes.
 */
interface PlaceRepository {
    suspend fun search(query: PlaceQuery): List<Place>
    suspend fun getPlace(id: Long): Place?
    /** Every ACTIVE place, sorted by name. Meant for browse screens, not for the planner. */
    suspend fun allPlaces(): List<Place>
    suspend fun cuisineGroups(): List<CuisineGroup>
    suspend fun priceBrackets(): List<PriceBracket>
    suspend fun venueTypesFor(slot: MealSlot): Set<VenueType>
}

class RoomPlaceRepository(
    private val placeDao: PlaceDao,
    private val referenceDao: ReferenceDao,
) : PlaceRepository {

    @Volatile private var groupsCache: List<CuisineGroup>? = null
    @Volatile private var slotsCache: Map<MealSlot, Set<VenueType>>? = null

    override suspend fun search(query: PlaceQuery): List<Place> {
        val (minLat, maxLat, minLng, maxLng) = Geo.boundingBox(query.center, query.radiusMeters).toList()
        val groups = cuisineGroups().filter { it.code in query.cuisineGroups }
        val rows = placeDao.searchInBox(
            minLat = minLat, maxLat = maxLat, minLng = minLng, maxLng = maxLng,
            venueTypes = (query.venueTypes ?: VenueType.entries.toSet()).map { it.name },
            includeChains = query.includeChains,
            maxPrice = query.maxPrice,
            groupFilter = groups.isNotEmpty(),
            cuisines = groups.flatMap { it.cuisines }.distinct(),
            groupVenueTypes = groups.mapNotNull { it.venueType?.name },
            strictDietary = query.strictDietary
                ?.takeIf { it == Dietary.VEGAN_ONLY || it == Dietary.VEGETARIAN_ONLY }?.name,
        )
        return rows.asSequence()
            .map { it.toPlace(query.center) }
            .filter { it.distanceMeters!! <= query.radiusMeters }
            .filter { p -> query.openAt == null || p.openingHours?.isOpenAt(query.openAt) ?: true }
            .sortedBy { it.distanceMeters }
            .take(query.limit)
            .toList()
    }

    override suspend fun getPlace(id: Long): Place? = placeDao.getById(id)?.toPlace(null)

    override suspend fun allPlaces(): List<Place> = placeDao.getAllActive().map { it.toPlace(null) }

    override suspend fun cuisineGroups(): List<CuisineGroup> =
        groupsCache ?: referenceDao.cuisineGroups().map {
            CuisineGroup(
                code = it.group.code,
                icon = it.group.icon,
                label = LocalizedText(it.group.labelFr, it.group.labelEn),
                cuisines = it.cuisines.toSet(),
                venueType = it.group.venueType,
            )
        }.also { groupsCache = it }

    override suspend fun priceBrackets(): List<PriceBracket> =
        referenceDao.priceBrackets().map { PriceBracket(it.code, it.label, it.maxPrice) }

    override suspend fun venueTypesFor(slot: MealSlot): Set<VenueType> {
        val map = slotsCache ?: referenceDao.venueTypeSlots()
            .groupBy({ MealSlot.valueOf(it.slot) }, { it.venueType })
            .mapValues { it.value.toSet() }
            .also { slotsCache = it }
        return map[slot].orEmpty()
    }

    private fun PlaceWithTags.toPlace(from: LatLng?): Place {
        val p = place
        val loc = LatLng(p.lat, p.lng)
        return Place(
            id = p.id,
            name = p.name,
            address = p.address,
            location = loc,
            borough = p.borough,
            venueType = p.venueType,
            cuisines = cuisines,
            dietary = dietary.mapNotNull { d -> Dietary.entries.firstOrNull { it.name == d } }.toSet(),
            isChain = p.isChain,
            price = if (p.priceSource == PriceSource.NONE || p.priceMin == null) null
            else PriceRange(p.priceMin, p.priceMax, estimated = p.priceSource == PriceSource.GOOGLE_LEVEL_ESTIMATE),
            description = LocalizedText(p.descriptionFr, p.descriptionEn),
            descriptionSource = p.descriptionSource,
            rating = p.rating,
            ratingCount = p.ratingCount,
            openingHours = OpeningHours.parse(p.openingHours),
            photoRef = p.photoRef,
            googlePlaceId = p.googlePlaceId,
            distanceMeters = from?.let { Geo.distanceMeters(it, loc) },
        )
    }
}
