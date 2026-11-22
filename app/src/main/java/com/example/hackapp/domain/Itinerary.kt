package com.example.hackapp.domain

import com.example.hackapp.data.model.LatLng
import com.example.hackapp.data.model.MealSlot
import com.example.hackapp.ui.theme.StopType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
}

/** What a Stop costs per person. Unknown prices are kept but labelled and ranked lower. */
@Serializable
data class StopPrice(val free: Boolean = false, val text: String? = null) {
    val isUnknown get() = !free && text == null
}

/** One Event or Meal of an Itinerary. Times are local to the city. */
@Serializable
data class Stop(
    val id: String,
    val type: StopType,
    val title: String,
    val details: String? = null,
    val districtId: String,
    val lat: Double? = null,
    val lng: Double? = null,
    @Serializable(with = LocalDateTimeSerializer::class) val start: LocalDateTime,
    /** When the person leaves (the Event's end, or what they chose when it is unpublished). */
    @Serializable(with = LocalDateTimeSerializer::class) val departure: LocalDateTime,
    val price: StopPrice = StopPrice(),
) {
    val isMeal get() = type == StopType.Meal || type == StopType.Bar
    val location: LatLng? get() = if (lat != null && lng != null) LatLng(lat, lng) else null
}

/** A Meal Block: the meal hour plus a 15 minute margin on each side. */
data class MealBlock(val slot: MealSlot, val mealStart: LocalDateTime) {
    val blockStart: LocalDateTime get() = mealStart.minusMinutes(MARGIN_MINUTES)
    val blockEnd: LocalDateTime get() = mealStart.plusMinutes(MEAL_MINUTES + MARGIN_MINUTES)

    companion object {
        const val MEAL_MINUTES = 60L
        const val MARGIN_MINUTES = 15L
    }
}

enum class ItineraryKind { DAY_OUT, NIGHT_OUT }

/** The window a person plans: it belongs to the date it starts on, even past midnight. */
data class PlanWindow(val date: LocalDate, val start: LocalTime, val end: LocalTime) {
    val startAt: LocalDateTime get() = date.atTime(start)
    val endAt: LocalDateTime get() = if (end.isAfter(start)) date.atTime(end) else date.plusDays(1).atTime(end)

    companion object {
        fun default(date: LocalDate, kind: ItineraryKind) = when (kind) {
            ItineraryKind.DAY_OUT -> PlanWindow(date, LocalTime.of(10, 0), LocalTime.of(18, 0))
            ItineraryKind.NIGHT_OUT -> PlanWindow(date, LocalTime.of(18, 0), LocalTime.of(2, 0))
        }
    }
}

/** The Itinerary being built; once started it is the plan being followed. */
@Serializable
data class Itinerary(
    val date: String,
    val districts: List<String>,
    val stops: List<Stop>,
)

enum class StopStatus { PENDING, CONFIRMED, SKIPPED }

/** Progress through a started Itinerary. [index] is the Stop being travelled to or visited. */
@Serializable
data class UnderwayProgress(
    val itinerary: Itinerary,
    val index: Int = 0,
    val statuses: List<StopStatus> = itinerary.stops.map { StopStatus.PENDING },
    val atStop: Boolean = false,
    val useRideHail: Boolean = false,
    val fromLat: Double? = null,
    val fromLng: Double? = null,
) {
    val current: Stop? get() = itinerary.stops.getOrNull(index)
    val finished get() = index >= itinerary.stops.size
}

fun Stop.durationMinutes(): Long = Duration.between(start, departure).toMinutes()
