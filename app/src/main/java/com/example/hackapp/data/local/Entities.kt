package com.example.hackapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.hackapp.data.model.AreaKind
import com.example.hackapp.data.model.DescriptionSource
import com.example.hackapp.data.model.PlaceStatus
import com.example.hackapp.data.model.PriceSource
import com.example.hackapp.data.model.VenueType

/*
 * These entities MUST match etl/sql/schema.sql exactly (names, nullability, keys, indices).
 * Room validates the prepackaged places.db against them on first open.
 */

@Entity(
    tableName = "place",
    indices = [
        Index(value = ["city_id"], unique = true),
        Index(value = ["lat", "lng"]),
        Index(value = ["borough"]),
    ],
)
data class PlaceEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "city_id") val cityId: String,
    @ColumnInfo(name = "osm_id") val osmId: String?,
    @ColumnInfo(name = "google_place_id") val googlePlaceId: String?,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "lat") val lat: Double,
    @ColumnInfo(name = "lng") val lng: Double,
    @ColumnInfo(name = "borough") val borough: String?,
    @ColumnInfo(name = "venue_type") val venueType: VenueType,
    @ColumnInfo(name = "is_chain") val isChain: Boolean,
    /** Per person, CAD, inclusive. */
    @ColumnInfo(name = "price_min") val priceMin: Int?,
    /** Per person, CAD, exclusive. Null with GOOGLE_RANGE = no upper bound ("$100+"). */
    @ColumnInfo(name = "price_max") val priceMax: Int?,
    @ColumnInfo(name = "price_source") val priceSource: PriceSource,
    @ColumnInfo(name = "description_fr") val descriptionFr: String?,
    @ColumnInfo(name = "description_en") val descriptionEn: String?,
    @ColumnInfo(name = "description_source") val descriptionSource: DescriptionSource,
    @ColumnInfo(name = "rating") val rating: Double?,
    @ColumnInfo(name = "rating_count") val ratingCount: Int?,
    /** JSON, see [com.example.hackapp.data.model.OpeningHours]. */
    @ColumnInfo(name = "opening_hours") val openingHours: String?,
    @ColumnInfo(name = "photo_ref") val photoRef: String?,
    @ColumnInfo(name = "status") val status: PlaceStatus,
    @ColumnInfo(name = "status_verified_at") val statusVerifiedAt: String,
    @ColumnInfo(name = "enriched_at") val enrichedAt: String?,
)

@Entity(
    tableName = "place_cuisine",
    primaryKeys = ["place_id", "cuisine"],
    foreignKeys = [ForeignKey(
        entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["place_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["cuisine"])],
)
data class PlaceCuisineEntity(
    @ColumnInfo(name = "place_id") val placeId: Long,
    @ColumnInfo(name = "cuisine") val cuisine: String,
)

@Entity(
    tableName = "place_dietary",
    primaryKeys = ["place_id", "dietary"],
    foreignKeys = [ForeignKey(
        entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["place_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["dietary"])],
)
data class PlaceDietaryEntity(
    @ColumnInfo(name = "place_id") val placeId: Long,
    /** A [com.example.hackapp.data.model.Dietary] constant name. */
    @ColumnInfo(name = "dietary") val dietary: String,
)

@Entity(tableName = "area")
data class AreaEntity(
    @PrimaryKey @ColumnInfo(name = "code") val code: String,
    @ColumnInfo(name = "kind") val kind: AreaKind,
    @ColumnInfo(name = "name_fr") val nameFr: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
)

@Entity(tableName = "cuisine_group")
data class CuisineGroupEntity(
    @PrimaryKey @ColumnInfo(name = "code") val code: String,
    @ColumnInfo(name = "emoji") val emoji: String,
    @ColumnInfo(name = "label_fr") val labelFr: String,
    @ColumnInfo(name = "label_en") val labelEn: String,
    /** Set for groups that match a venue type instead of cuisines (e.g. "bars"). */
    @ColumnInfo(name = "venue_type") val venueType: VenueType?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "cuisine_group_member",
    primaryKeys = ["group_code", "cuisine"],
    foreignKeys = [ForeignKey(
        entity = CuisineGroupEntity::class, parentColumns = ["code"], childColumns = ["group_code"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["cuisine"])],
)
data class CuisineGroupMemberEntity(
    @ColumnInfo(name = "group_code") val groupCode: String,
    @ColumnInfo(name = "cuisine") val cuisine: String,
)

@Entity(tableName = "price_bracket")
data class PriceBracketEntity(
    @PrimaryKey @ColumnInfo(name = "code") val code: String,
    @ColumnInfo(name = "label") val label: String,
    /** Exclusive upper bound per person (CAD); null = no limit. */
    @ColumnInfo(name = "max_price") val maxPrice: Int?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(tableName = "venue_type_slot", primaryKeys = ["venue_type", "slot"])
data class VenueTypeSlotEntity(
    @ColumnInfo(name = "venue_type") val venueType: VenueType,
    /** A [com.example.hackapp.data.model.MealSlot] constant name. */
    @ColumnInfo(name = "slot") val slot: String,
)

// ---------------------------------------------------------------- relations

data class PlaceWithTags(
    @Embedded val place: PlaceEntity,
    @Relation(
        parentColumn = "id", entityColumn = "place_id",
        entity = PlaceCuisineEntity::class, projection = ["cuisine"],
    )
    val cuisines: List<String>,
    @Relation(
        parentColumn = "id", entityColumn = "place_id",
        entity = PlaceDietaryEntity::class, projection = ["dietary"],
    )
    val dietary: List<String>,
)

data class CuisineGroupWithMembers(
    @Embedded val group: CuisineGroupEntity,
    @Relation(
        parentColumn = "code", entityColumn = "group_code",
        entity = CuisineGroupMemberEntity::class, projection = ["cuisine"],
    )
    val cuisines: List<String>,
)
