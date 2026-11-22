package com.example.hackapp.data.remote.hasthings

import com.example.hackapp.data.Cost
import com.example.hackapp.data.CostStatus
import com.example.hackapp.data.Event
import com.example.hackapp.data.Location
import com.example.hackapp.data.Schedule
import com.example.hackapp.data.TargetAudience
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val DEFAULT_TIMEZONE = "America/Toronto"
private val OFFSET_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

/**
 * Maps a Has Things showtime to [Event], matching the `events_toronto.csv` export:
 * id `hasthings:<id>`, type = category, categories = tag labels,
 * dates converted from UTC to the city's local offset.
 */
fun HasThingsEventDto.toEvent(city: HasThingsCityDto?, fallbackCityName: String): Event {
    val timezone = city?.timezone ?: DEFAULT_TIMEZONE
    val zone = ZoneId.of(timezone)

    return Event(
        id = "hasthings:$id",
        title = title,
        description = null, // bulk feeds omit descriptions
        url = url,
        type = category ?: "other",
        categories = tags.mapNotNull { it.label }.distinct().ifEmpty { null },
        targetAudience = ageRestriction?.let { TargetAudience(description = it) },
        location = Location(
            venueName = venue?.name,
            city = city?.name ?: fallbackCityName,
            borough = venue?.neighbourhood,
        ),
        schedule = Schedule(
            startDate = startsAt.toLocalIso(zone) ?: startsAt,
            endDate = endsAt?.let { it.toLocalIso(zone) ?: it },
            timezone = timezone,
        ),
        cost = Cost(status = CostStatus.UNKNOWN), // feed has no price info
    )
}

private fun String.toLocalIso(zone: ZoneId): String? =
    runCatching { Instant.parse(this).atZone(zone).format(OFFSET_FORMAT) }.getOrNull()
