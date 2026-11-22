package com.example.hackapp.data.model

import org.json.JSONArray
import java.time.ZonedDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

/** App-facing place. Built from the Room entity by the repository. */
data class Place(
    val id: Long,
    val name: String,
    val address: String,
    val location: LatLng,
    val borough: String?,
    val venueType: VenueType,
    val cuisines: List<String>,
    val dietary: Set<Dietary>,
    val isChain: Boolean,
    val price: PriceRange?,
    val description: LocalizedText,
    val descriptionSource: DescriptionSource,
    val rating: Double?,
    val ratingCount: Int?,
    val openingHours: OpeningHours?,
    val photoRef: String?,
    val googlePlaceId: String?,
    /** Filled by queries that have a reference point. */
    val distanceMeters: Double? = null,
)

/** Per person, CAD. [max] is exclusive; null max = no upper bound ("$100+"). */
data class PriceRange(val min: Int, val max: Int?, val estimated: Boolean) {
    fun label(): String = if (max == null) "$$min+" else "$$min–$max"
}

data class LocalizedText(val fr: String?, val en: String?) {
    fun get(languageTag: String): String? = if (languageTag.startsWith("fr")) fr ?: en else en ?: fr
}

data class CuisineGroup(
    val code: String,
    /** Material icon name, e.g. "ramen_dining". */
    val icon: String,
    val label: LocalizedText,
    val cuisines: Set<String>,
    val venueType: VenueType?,
)

data class PriceBracket(val code: String, val label: String, val maxPrice: Int?)

/**
 * Weekly opening hours as stored by the ETL:
 * `[{"od":1,"o":"11:00","cd":1,"c":"21:30"}, ...]`, days 0 = Sunday ... 6 = Saturday (Google).
 * A period without "cd"/"c" means open 24/7.
 */
class OpeningHours private constructor(private val periods: List<IntRange>, val alwaysOpen: Boolean) {

    fun isOpenAt(time: ZonedDateTime): Boolean {
        if (alwaysOpen) return true
        val day = time.dayOfWeek.value % 7 // MONDAY=1..SUNDAY=7 -> SUNDAY=0
        val t = day * DAY + time.hour * 60 + time.minute
        return periods.any { t in it || (t + WEEK) in it }
    }

    companion object {
        private const val DAY = 24 * 60
        private const val WEEK = 7 * DAY

        private fun minutes(day: Int, hhmm: String): Int {
            val (h, m) = hhmm.split(":").map { it.toInt() }
            return day * DAY + h * 60 + m
        }

        fun parse(json: String?): OpeningHours? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val arr = JSONArray(json)
                val ranges = mutableListOf<IntRange>()
                var always = false
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    if (!p.has("c")) { always = true; continue }
                    val open = minutes(p.getInt("od"), p.getString("o"))
                    var close = minutes(p.getInt("cd"), p.getString("c"))
                    if (close <= open) close += WEEK // wraps past Saturday night
                    ranges += open until close
                }
                OpeningHours(ranges, always)
            }.getOrNull()
        }
    }
}

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMeters(a: LatLng, b: LatLng): Double {
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val dp = p2 - p1
        val dl = Math.toRadians(b.lng - a.lng)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /** (minLat, maxLat, minLng, maxLng) of a box that contains the circle. */
    fun boundingBox(center: LatLng, radiusMeters: Double): DoubleArray {
        val dLat = Math.toDegrees(radiusMeters / EARTH_RADIUS_M)
        val dLng = Math.toDegrees(radiusMeters / (EARTH_RADIUS_M * cos(Math.toRadians(center.lat))))
        return doubleArrayOf(center.lat - dLat, center.lat + dLat, center.lng - dLng, center.lng + dLng)
    }
}
