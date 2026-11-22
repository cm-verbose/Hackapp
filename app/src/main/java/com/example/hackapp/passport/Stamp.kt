package com.example.hackapp.passport

import com.example.hackapp.itinerary.LocalDateIsoSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate

/* Constant names are stored in saved Stamps: renaming one is a data migration. */

@Serializable
enum class StampKind { Event, Meal }

/**
 * One Passport entry. [key] is the unrepeatable identity: one Stamp per [key] per person, so it
 * is also the record id. It carries the data the Passport shows, so a Stamp never needs the
 * Itinerary or the Place again.
 */
@Serializable
data class PassportStamp(
    /** `<cityId>:event:<id>`, `<cityId>:place:<id>` or `<cityId>:chain:<name>`. */
    val key: String,
    val ownerUid: String,
    val cityId: String,
    val kind: StampKind,
    /** The Event or restaurant name. For a chain, the first location visited. */
    val name: String,
    /** The date of the Itinerary, even for a Stop after midnight. */
    @Serializable(LocalDateIsoSerializer::class) val date: LocalDate,
    val districtId: String,
    /** The Event type or the venue type in lower case, when known. */
    val category: String? = null,
    /** The Itinerary and Stop that earned it. They let a retry recognise its own Stamp. */
    val itineraryId: String,
    val stopId: String,
    val awardedAtMillis: Long,
)
