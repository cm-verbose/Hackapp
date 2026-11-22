package com.example.hackapp.data.remote.hasthings

import com.example.hackapp.data.Event
import com.example.hackapp.data.remote.Network
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Has Things public feeds (https://<city>.hasthings.com/feeds).
 * No key required; capped at 1000 listings and a 60-day window.
 */
interface HasThingsApi {
    @GET("feeds/events.json")
    suspend fun events(@Query("days") days: Int? = null): HasThingsFeedDto
}

/** Supported Has Things cities. */
enum class HasThingsCity(val baseUrl: String, val fallbackName: String) {
    TORONTO("https://toronto.hasthings.com/", "Toronto"),
    MONTREAL("https://montreal.hasthings.com/", "Montréal"),
}

/** Fetches Has Things events for one city and maps them to [Event]. */
class HasThingsEventSource(private val city: HasThingsCity) {

    private val api: HasThingsApi = Network.retrofit(city.baseUrl).create(HasThingsApi::class.java)

    suspend fun fetchEvents(days: Int? = null): List<Event> {
        val feed = api.events(days)
        return feed.items.map { it.toEvent(feed.city, city.fallbackName) }
    }
}
