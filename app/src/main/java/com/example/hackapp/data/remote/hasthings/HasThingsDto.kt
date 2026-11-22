package com.example.hackapp.data.remote.hasthings

import kotlinx.serialization.Serializable

/** Envelope returned by `https://<city>.hasthings.com/feeds/events.json`. */
@Serializable
data class HasThingsFeedDto(
    val city: HasThingsCityDto? = null,
    val count: Int? = null,
    val truncated: Boolean? = null,
    val items: List<HasThingsEventDto> = emptyList(),
)

@Serializable
data class HasThingsCityDto(
    val slug: String? = null,
    val name: String? = null,
    val timezone: String? = null,
    val countryCode: String? = null,
    val url: String? = null,
)

/** One showtime. `eventId` repeats across showtimes of the same event. */
@Serializable
data class HasThingsEventDto(
    val id: String,
    val eventId: String? = null,
    val slug: String? = null,
    val title: String,
    val category: String? = null,
    val status: String? = null,
    val startsAt: String,
    val startTimeKnown: Boolean? = null,
    val endsAt: String? = null,
    val doorsAt: String? = null,
    val nightDate: String? = null,
    val ageRestriction: String? = null,
    val importance: Int? = null,
    val outdoors: Boolean? = null,
    val venue: HasThingsVenueDto? = null,
    val url: String? = null,
    val ticketUrl: String? = null,
    val sources: List<String> = emptyList(),
    val tags: List<HasThingsTagDto> = emptyList(),
)

@Serializable
data class HasThingsVenueDto(
    val slug: String? = null,
    val name: String? = null,
    val neighbourhood: String? = null,
)

@Serializable
data class HasThingsTagDto(
    val kind: String? = null,
    val tag: String? = null,
    val label: String? = null,
)
