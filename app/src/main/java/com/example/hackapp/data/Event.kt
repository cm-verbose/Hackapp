package com.example.hackapp.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Event(
    // Identification
    val id: String,
    val title: String,
    val description: String? = null,
    val url: String? = null,

    // Classification
    val type: String,
    val categories: List<String>? = null,

    // Target audience
    val targetAudience: TargetAudience? = null,

    // Location
    val location: Location,

    // Schedule
    val schedule: Schedule,

    // Cost
    val cost: Cost,
)

@Serializable
data class TargetAudience(
    val description: String? = null,
    val minAge: Int? = null,
    val maxAge: Int? = null,
)

@Serializable
data class Location(
    val venueName: String? = null,
    val address: String? = null,
    val city: String,
    val borough: String? = null,
    val postalCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class Schedule(
    val startDate: String,
    val endDate: String? = null,
    val timezone: String,
)

@Serializable
data class Cost(
    val status: CostStatus,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val currency: String? = null,
    val originalText: String? = null,
    val registrationRequired: Boolean? = null,
)

@Serializable
enum class CostStatus {
    @SerialName("free") FREE,
    @SerialName("paid") PAID,
    @SerialName("unknown") UNKNOWN,
}
