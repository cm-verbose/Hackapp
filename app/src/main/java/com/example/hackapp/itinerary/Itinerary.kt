package com.example.hackapp.itinerary

import com.example.hackapp.profile.BudgetBracket
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/* Constant names are stored in saved Itineraries: renaming one is a data migration. */

@Serializable
enum class ItineraryPhase { Planning, Underway, Completed, Archived }

@Serializable
enum class OutingKind { Day, Night }

/** The Planning step a draft resumes on. */
@Serializable
enum class PlanningStep { DateTime, Districts, Meals, Stops, Review }

/** [blockMinutes] is the whole Meal Block, including its margins. */
@Serializable
enum class MealKind(val blockMinutes: Int) {
    Lunch(90),
    Dinner(90),
    LateBite(45),
}

@Serializable
enum class StopKind { Event, Meal }

@Serializable
enum class StopStatus { Planned, Confirmed, Skipped }

/** Time reserved for a Meal. A skipped block no longer hides Events. */
@Serializable
data class MealBlock(
    val id: String,
    val kind: MealKind,
    @Serializable(LocalTimeIsoSerializer::class) val start: LocalTime,
    val budget: BudgetBracket? = null,
    val skipped: Boolean = false,
)

/** A place on an Itinerary. [sourceId] is the Event id, or the Place id as text. */
@Serializable
data class Stop(
    val id: String,
    val kind: StopKind,
    val sourceId: String,
    val name: String,
    val districtId: String,
    val latitude: Double,
    val longitude: Double,
    @Serializable(OffsetDateTimeIsoSerializer::class) val start: OffsetDateTime,
    /** The time the person intends to leave this Stop. */
    @Serializable(OffsetDateTimeIsoSerializer::class) val plannedDeparture: OffsetDateTime,
    /** False when the source published no end time and the person chose the departure. */
    val hasPublishedEnd: Boolean = false,
    val mealBlockId: String? = null,
    val status: StopStatus = StopStatus.Planned,
    /** This person's own reported ticket price in CAD, if any. */
    val ticketPriceCad: Double? = null,
)

/** A single day out or night out, from Planning through completion. */
@Serializable
data class Itinerary(
    val id: String,
    val ownerUid: String,
    val cityId: String,
    val phase: ItineraryPhase = ItineraryPhase.Planning,
    val step: PlanningStep = PlanningStep.DateTime,
    /** The date it starts on, even when its hours continue after midnight. */
    @Serializable(LocalDateIsoSerializer::class) val date: LocalDate,
    val outingKind: OutingKind,
    @Serializable(LocalTimeIsoSerializer::class) val startTime: LocalTime,
    @Serializable(LocalTimeIsoSerializer::class) val endTime: LocalTime,
    /** Chosen Districts in visit order. */
    val districtIds: List<String> = emptyList(),
    /** Null until the Meal choice step is answered. */
    val planMeals: Boolean? = null,
    val mealBlocks: List<MealBlock> = emptyList(),
    val eventBudget: BudgetBracket? = null,
    /** Stops in visit order. */
    val stops: List<Stop> = emptyList(),
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    /** Places a clock time inside this window. Times before [startTime] belong to the next day. */
    fun resolve(time: LocalTime, zone: ZoneId): ZonedDateTime {
        val day = if (time < startTime) date.plusDays(1) else date
        return day.atTime(time).atZone(zone)
    }

    fun windowStart(zone: ZoneId): ZonedDateTime = date.atTime(startTime).atZone(zone)

    fun windowEnd(zone: ZoneId): ZonedDateTime {
        val endDay = if (endTime <= startTime) date.plusDays(1) else date
        return endDay.atTime(endTime).atZone(zone)
    }
}

object LocalDateIsoSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateIso", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object LocalTimeIsoSerializer : KSerializer<LocalTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalTimeIso", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalTime = LocalTime.parse(decoder.decodeString())
}

object OffsetDateTimeIsoSerializer : KSerializer<OffsetDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("OffsetDateTimeIso", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: OffsetDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): OffsetDateTime = OffsetDateTime.parse(decoder.decodeString())
}
