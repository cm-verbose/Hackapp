package com.example.hackapp.data.local

import com.example.hackapp.data.Cost
import com.example.hackapp.data.CostStatus
import com.example.hackapp.data.Event
import com.example.hackapp.data.Location
import com.example.hackapp.data.Schedule
import com.example.hackapp.data.TargetAudience

/**
 * Maps a CSV row in the flattened [Event] layout
 * (`location_city`, `schedule_startDate`, `cost_status`, …) to [Event].
 * Columns are looked up by name, so extra columns (e.g. `source`) are ignored.
 * Returns null if a required column is missing.
 */
fun Map<String, String>.toEventOrNull(defaultCity: String, defaultTimezone: String): Event? {
    val id = this["id"] ?: return null
    val title = this["title"] ?: return null
    val startDate = this["schedule_startDate"] ?: return null

    val audienceDescription = this["targetAudience_description"]
    val minAge = this["targetAudience_minAge"]?.toIntOrNull()
    val maxAge = this["targetAudience_maxAge"]?.toIntOrNull()

    return Event(
        id = id,
        title = title,
        description = this["description"],
        url = this["url"],
        type = this["type"] ?: "other",
        categories = this["categories"]?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() }?.ifEmpty { null },
        targetAudience = if (audienceDescription != null || minAge != null || maxAge != null) {
            TargetAudience(audienceDescription, minAge, maxAge)
        } else null,
        location = Location(
            venueName = this["location_venueName"],
            address = this["location_address"],
            city = this["location_city"] ?: defaultCity,
            borough = this["location_borough"],
            postalCode = this["location_postalCode"],
            latitude = this["location_latitude"]?.toDoubleOrNull(),
            longitude = this["location_longitude"]?.toDoubleOrNull(),
        ),
        schedule = Schedule(
            startDate = startDate,
            endDate = this["schedule_endDate"],
            timezone = this["schedule_timezone"] ?: defaultTimezone,
        ),
        cost = Cost(
            status = when (this["cost_status"]?.lowercase()) {
                "free" -> CostStatus.FREE
                "paid" -> CostStatus.PAID
                else -> CostStatus.UNKNOWN
            },
            minPrice = this["cost_minPrice"]?.toDoubleOrNull(),
            maxPrice = this["cost_maxPrice"]?.toDoubleOrNull(),
            currency = this["cost_currency"],
            originalText = this["cost_originalText"],
            registrationRequired = this["cost_registrationRequired"]?.lowercase()?.toBooleanStrictOrNull(),
        ),
    )
}
