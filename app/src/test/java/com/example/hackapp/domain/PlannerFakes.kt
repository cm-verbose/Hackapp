package com.example.hackapp.domain

import com.example.hackapp.data.Cost
import com.example.hackapp.data.CostStatus
import com.example.hackapp.data.Event
import com.example.hackapp.data.EventStatus
import com.example.hackapp.data.Location
import com.example.hackapp.data.Schedule
import com.example.hackapp.data.TargetAudience
import com.example.hackapp.data.model.CuisineGroup
import com.example.hackapp.data.model.DescriptionSource
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.data.model.LocalizedText
import com.example.hackapp.data.model.MealSlot
import com.example.hackapp.data.model.Place
import com.example.hackapp.data.model.PriceBracket
import com.example.hackapp.data.model.VenueType
import com.example.hackapp.data.repository.EventQuery
import com.example.hackapp.data.repository.EventRepository
import com.example.hackapp.data.repository.PlaceQuery
import com.example.hackapp.data.repository.PlaceRepository
import com.example.hackapp.services.LegEstimate
import com.example.hackapp.services.LegEstimator
import com.example.hackapp.ui.theme.StopType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** Test doubles for [ItineraryPlanner] (T13a). No Android, no new dependencies. */
internal val ZONE: ZoneId = ZoneId.of("America/Toronto")
internal val DAY: LocalDate = LocalDate.of(2026, 11, 21)
internal const val DISTRICT = "ville-marie"
internal val CENTER = LatLng(45.5075, -73.5540)

/** An Event on [DAY] in [DISTRICT]; times are "HH:mm" in Montréal time (EST, -05:00). */
internal fun ev(
    id: String,
    start: String,
    end: String? = null,
    type: String = "music",
    district: String = DISTRICT,
    lat: Double? = CENTER.lat,
    lng: Double? = CENTER.lng,
    audience: TargetAudience? = null,
    status: EventStatus = EventStatus.SCHEDULED,
) = Event(
    id = id, title = id, type = type, targetAudience = audience,
    location = Location(city = "Montréal", latitude = lat, longitude = lng),
    schedule = Schedule("${DAY}T$start:00-05:00", end?.let { "${DAY}T$it:00-05:00" }, ZONE.id),
    cost = Cost(CostStatus.FREE), districtId = district, status = status,
)

internal fun at(hhmm: String): LocalDateTime = DAY.atTime(hhmm.substringBefore(':').toInt(), hhmm.substringAfter(':').toInt())

internal fun stop(id: String, start: String, departure: String, district: String = DISTRICT, location: LatLng? = CENTER) = Stop(
    id = id, type = StopType.Music, title = id, districtId = district,
    lat = location?.lat, lng = location?.lng, start = at(start), departure = at(departure),
)

/** Filters like the snapshot repository: district and start bounds. */
internal class FakeEventRepository(private val events: List<Event>) : EventRepository {
    override suspend fun search(query: EventQuery): List<Event> = events.filter { e ->
        val start = OffsetDateTime.parse(e.schedule.startDate)
        (query.districtIds == null || e.districtId in query.districtIds) &&
            (query.startsAtOrAfter == null || !start.isBefore(query.startsAtOrAfter)) &&
            (query.startsBefore == null || start.isBefore(query.startsBefore))
    }.sortedBy { OffsetDateTime.parse(it.schedule.startDate) }

    override suspend fun getEvent(id: String) = events.firstOrNull { it.id == id }
    override suspend fun countByDistrict(query: EventQuery) =
        search(query).groupingBy { it.districtId!! }.eachCount()
}

internal fun place(
    id: Long,
    type: VenueType = VenueType.RESTAURANT,
    borough: String? = DISTRICT,
    cuisines: List<String> = emptyList(),
    distance: Double = 100.0,
    location: LatLng = CENTER,
) = Place(
    id = id, name = "p$id", address = "a$id", location = location, borough = borough, venueType = type,
    cuisines = cuisines, dietary = emptySet(), isChain = false, price = null,
    description = LocalizedText(null, null), descriptionSource = DescriptionSource.GENERATED,
    rating = null, ratingCount = null, openingHours = null, photoRef = null, googlePlaceId = null,
    distanceMeters = distance,
)

/** Returns [places] filtered by the query's venue types; records every query for assertions. */
internal class FakePlaceRepository(
    private val places: List<Place>,
    private val slots: Map<MealSlot, Set<VenueType>> = mapOf(
        MealSlot.LUNCH to setOf(VenueType.RESTAURANT, VenueType.FAST_FOOD),
        MealSlot.DINNER to setOf(VenueType.RESTAURANT, VenueType.FAST_FOOD),
        MealSlot.EVENING to setOf(VenueType.BAR),
    ),
    private val groups: List<CuisineGroup> = emptyList(),
) : PlaceRepository {
    val queries = mutableListOf<PlaceQuery>()

    override suspend fun search(query: PlaceQuery): List<Place> {
        queries += query
        return places.filter { query.venueTypes == null || it.venueType in query.venueTypes!! }
    }

    override suspend fun getPlace(id: Long) = places.firstOrNull { it.id == id }
    override suspend fun allPlaces() = places
    override suspend fun cuisineGroups() = groups
    override suspend fun priceBrackets() = listOf(PriceBracket("UNDER_50", "< $50", 50))
    override suspend fun venueTypesFor(slot: MealSlot) = slots[slot].orEmpty()
}

/** Every Leg takes [travel] minutes plus [buffer] minutes. */
internal fun fixedLegs(travel: Int = 10, buffer: Int = 5) = LegEstimator { _, _, _ -> LegEstimate(1000.0, travel, buffer) }

/** "Now" for the planner in tests: the start of [DAY], so every fixture Event is still to come. */
internal val START_OF_DAY: java.time.Clock = java.time.Clock.fixed(DAY.atStartOfDay(ZONE).toInstant(), ZONE)

internal fun planner(
    events: List<Event> = emptyList(),
    places: PlaceRepository = FakePlaceRepository(emptyList()),
    legs: LegEstimator = fixedLegs(),
    clock: java.time.Clock = START_OF_DAY,
) = ItineraryPlanner(FakeEventRepository(events), { places }, legs, ZONE, clock)

internal fun input(
    window: PlanWindow = PlanWindow(DAY, java.time.LocalTime.of(10, 0), java.time.LocalTime.of(23, 0)),
    audience: Audience = Audience.ADULTS_ONLY,
    budgetMax: Int? = null,
    profile: UserProfile = UserProfile(),
) = PlanInput(window, audience, budgetMax, profile)
