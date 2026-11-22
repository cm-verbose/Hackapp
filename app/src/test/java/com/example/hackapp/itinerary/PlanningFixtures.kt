package com.example.hackapp.itinerary

import com.example.hackapp.data.Cost
import com.example.hackapp.data.CostStatus
import com.example.hackapp.data.Event
import com.example.hackapp.data.EventStatus
import com.example.hackapp.data.Location
import com.example.hackapp.data.Schedule
import com.example.hackapp.data.TargetAudience
import com.example.hackapp.data.model.DescriptionSource
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.data.model.LocalizedText
import com.example.hackapp.data.model.Place
import com.example.hackapp.data.model.PriceRange
import com.example.hackapp.data.model.VenueType
import com.example.hackapp.profile.AgeRange
import com.example.hackapp.services.LegEstimate
import com.example.hackapp.services.LegEstimator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

val Toronto: ZoneId = ZoneId.of("America/Toronto")
val Nov28: LocalDate = LocalDate.of(2026, 11, 28)
const val UID = "user-1"
const val HERE = "ville-marie"
const val NEXT = "plateau-mont-royal"

/** Every Leg takes [minutes] in total: travel plus a 10-minute buffer. */
fun fixedLegs(minutes: Int = 20) = LegEstimator { _, _, _ ->
    LegEstimate(distanceMeters = 1000.0, travelMinutes = minutes - 10, bufferMinutes = 10)
}

fun at(day: LocalDate, hour: Int, minute: Int = 0): OffsetDateTime =
    day.atTime(hour, minute).atZone(Toronto).toOffsetDateTime()

fun instant(day: LocalDate, hour: Int, minute: Int = 0): Instant = at(day, hour, minute).toInstant()

fun context(
    now: Instant = instant(Nov28, 8),
    ageRange: AgeRange = AgeRange.From25To34,
) = PlanningContext(now = now, zone = Toronto, ageRange = ageRange)

fun event(
    id: String,
    start: String? = at(Nov28, 19).toString(),
    end: String? = null,
    type: String = "music",
    title: String = "Title $id",
    venue: String? = "Venue $id",
    district: String? = HERE,
    status: EventStatus = EventStatus.SCHEDULED,
    audience: TargetAudience? = null,
    cost: Cost = Cost(CostStatus.UNKNOWN),
    latitude: Double? = 45.5,
) = Event(
    id = id,
    title = title,
    type = type,
    targetAudience = audience,
    location = Location(
        venueName = venue,
        city = "Montréal",
        latitude = latitude,
        longitude = latitude?.let { -73.6 },
    ),
    schedule = Schedule(startDate = start.orEmpty(), endDate = end, timezone = "America/Toronto"),
    cost = cost,
    districtId = district,
    status = status,
)

fun place(
    id: Long,
    district: String = HERE,
    venueType: VenueType = VenueType.RESTAURANT,
    price: PriceRange? = PriceRange(10, 20, estimated = false),
    cuisines: List<String> = emptyList(),
    location: LatLng = LatLng(45.5, -73.6),
    name: String = "Place $id",
) = Place(
    id = id,
    name = name,
    address = "1 rue Test",
    location = location,
    borough = district,
    venueType = venueType,
    cuisines = cuisines,
    dietary = emptySet(),
    isChain = false,
    price = price,
    description = LocalizedText(null, null),
    descriptionSource = DescriptionSource.GENERATED,
    rating = null,
    ratingCount = null,
    openingHours = null,
    photoRef = null,
    googlePlaceId = null,
)

fun stop(
    id: String,
    start: OffsetDateTime,
    departure: OffsetDateTime,
    kind: StopKind = StopKind.Event,
    district: String = HERE,
    sourceId: String = id,
    mealBlockId: String? = null,
    status: StopStatus = StopStatus.Planned,
    ticketPriceCad: Double? = null,
) = Stop(
    id = id,
    kind = kind,
    sourceId = sourceId,
    name = "Stop $id",
    districtId = district,
    latitude = 45.5,
    longitude = -73.6,
    start = start,
    plannedDeparture = departure,
    mealBlockId = mealBlockId,
    status = status,
    ticketPriceCad = ticketPriceCad,
)

fun night(
    id: String = "it-1",
    date: LocalDate = Nov28,
    stops: List<Stop> = emptyList(),
    blocks: List<MealBlock> = emptyList(),
    phase: ItineraryPhase = ItineraryPhase.Planning,
    start: LocalTime = LocalTime.of(18, 0),
    end: LocalTime = LocalTime.of(2, 0),
) = Itinerary(
    id = id,
    ownerUid = UID,
    cityId = "mtl",
    phase = phase,
    date = date,
    outingKind = OutingKind.Night,
    startTime = start,
    endTime = end,
    districtIds = listOf(HERE, NEXT),
    planMeals = blocks.isNotEmpty(),
    mealBlocks = blocks,
    stops = stops,
    createdAtMillis = 1,
    updatedAtMillis = 1,
)

fun day(
    date: LocalDate = Nov28,
    stops: List<Stop> = emptyList(),
    blocks: List<MealBlock> = emptyList(),
) = night(date = date, stops = stops, blocks = blocks, start = LocalTime.of(10, 0), end = LocalTime.of(18, 0))
    .copy(outingKind = OutingKind.Day)
