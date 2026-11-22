package com.example.hackapp.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PlaceDao {

    /**
     * Coarse search inside a lat/lng box (uses index_place_lat_lng). The repository then
     * computes the exact distance and the opening-hours filter.
     *
     * Parameters that are "off" use neutral values:
     * - maxPrice = null                 -> any price
     * - groupFilter = false             -> no cuisine-chip filter
     * - strictDietary = null            -> no strict diet filter
     */
    @Transaction
    @Query(
        """
        SELECT * FROM place
        WHERE status = 'ACTIVE'
          AND lat BETWEEN :minLat AND :maxLat
          AND lng BETWEEN :minLng AND :maxLng
          AND venue_type IN (:venueTypes)
          AND (:includeChains OR is_chain = 0)
          AND (:maxPrice IS NULL
               OR price_source = 'NONE'
               OR (price_max IS NOT NULL AND price_max <= :maxPrice))
          AND (NOT :groupFilter
               OR id IN (SELECT place_id FROM place_cuisine WHERE cuisine IN (:cuisines))
               OR venue_type IN (:groupVenueTypes))
          AND (:strictDietary IS NULL
               OR id IN (SELECT place_id FROM place_dietary WHERE dietary = :strictDietary))
        """
    )
    suspend fun searchInBox(
        minLat: Double, maxLat: Double, minLng: Double, maxLng: Double,
        venueTypes: List<String>,
        includeChains: Boolean,
        maxPrice: Int?,
        groupFilter: Boolean,
        cuisines: List<String>,
        groupVenueTypes: List<String>,
        strictDietary: String?,
    ): List<PlaceWithTags>

    @Transaction
    @Query("SELECT * FROM place WHERE id = :id")
    suspend fun getById(id: Long): PlaceWithTags?

    /** Every served place (no filters), for browse/debug screens. */
    @Transaction
    @Query("SELECT * FROM place WHERE status = 'ACTIVE' ORDER BY name COLLATE NOCASE")
    suspend fun getAllActive(): List<PlaceWithTags>

    @Query("SELECT COUNT(*) FROM place WHERE status = 'ACTIVE'")
    suspend fun countActive(): Int
}

@Dao
interface ReferenceDao {
    @Transaction
    @Query("SELECT * FROM cuisine_group ORDER BY sort_order")
    suspend fun cuisineGroups(): List<CuisineGroupWithMembers>

    @Query("SELECT * FROM price_bracket ORDER BY sort_order")
    suspend fun priceBrackets(): List<PriceBracketEntity>

    @Query("SELECT * FROM venue_type_slot")
    suspend fun venueTypeSlots(): List<VenueTypeSlotEntity>

    @Query("SELECT * FROM area WHERE kind = 'BOROUGH' ORDER BY name_fr")
    suspend fun boroughs(): List<AreaEntity>
}
