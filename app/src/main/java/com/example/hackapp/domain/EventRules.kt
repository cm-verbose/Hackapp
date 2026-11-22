package com.example.hackapp.domain

import com.example.hackapp.data.CostStatus
import com.example.hackapp.data.Event
import com.example.hackapp.data.EventStatus
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.ui.theme.StopType
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** Maps the snapshot's `type` to a [StopType]. Types with no match (`other`) are not offered. */
fun eventStopType(type: String): StopType? = when (type.lowercase()) {
    "music" -> StopType.Music
    "festival" -> StopType.Festival
    "theatre", "theater" -> StopType.Theatre
    "comedy" -> StopType.Comedy
    "exhibition" -> StopType.Exhibition
    "sport" -> StopType.Sport
    "cinema" -> StopType.Cinema
    "family" -> StopType.Family
    else -> null
}

fun Event.startLocal(zone: ZoneId): LocalDateTime? = runCatching {
    OffsetDateTime.parse(schedule.startDate).atZoneSameInstant(zone).toLocalDateTime()
}.getOrNull()

fun Event.endLocal(zone: ZoneId): LocalDateTime? = schedule.endDate?.let { end ->
    runCatching { OffsetDateTime.parse(end).atZoneSameInstant(zone).toLocalDateTime() }.getOrNull()
}

fun Event.latLng(): LatLng? = location.latitude?.let { lat -> location.longitude?.let { lng -> LatLng(lat, lng) } }

/** Whether the person may be offered this Event. Cancelled, sold-out and unknown-start Events never reach here. */
fun Event.isEligible(profile: UserProfile, audience: Audience): Boolean {
    if (status != EventStatus.SCHEDULED || eventStopType(type) == null) return false
    val minAge = targetAudience?.minAge
    val age = profile.ageRange
    if (minAge != null && age != null && minAge > age.min) return false
    val childrenOnly = targetAudience?.maxAge?.let { it < 13 } == true
    if (childrenOnly && audience != Audience.FAMILY_FRIENDLY) return false
    return true
}

fun Event.stopPrice(): StopPrice = when (cost.status) {
    CostStatus.FREE -> StopPrice(free = true)
    CostStatus.PAID -> StopPrice(text = cost.originalText ?: cost.minPrice?.let { "$${it.toInt()}" })
    CostStatus.UNKNOWN -> StopPrice()
}

/** Ranking score: interests and a known price lift an Event, never hide it. Higher is better. */
fun Event.rank(profile: UserProfile, budgetMax: Int?): Int {
    var score = 0
    if (eventStopType(type) in profile.interestTypes) score += 2
    val price = stopPrice()
    if (budgetMax != null && price.isUnknown) score -= 1
    if (budgetMax != null && cost.minPrice != null && cost.minPrice >= budgetMax) score -= 3
    return score
}
