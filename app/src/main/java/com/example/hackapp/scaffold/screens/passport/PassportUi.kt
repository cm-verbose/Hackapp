package com.example.hackapp.scaffold.screens.passport

import androidx.compose.runtime.Immutable
import com.example.hackapp.passport.PassportStamp
import com.example.hackapp.passport.PassportState
import com.example.hackapp.passport.StampKind
import com.example.hackapp.scaffold.screens.itinerary.eventStopType
import com.example.hackapp.ui.components.stampInitials
import com.example.hackapp.ui.theme.StopType
import java.time.format.DateTimeFormatter

/** One Stamp as the Passport shows it. */
@Immutable
data class PassportStampUi(
    val key: String,
    val initials: String,
    val name: String,
    val district: String,
    val date: String,
    val type: StopType,
    val shape: StampShape,
) {
    /** What TalkBack reads for the Stamp. */
    val description: String get() = listOf(name, district, date).joinToString(", ")
}

sealed interface PassportContent {
    data object Loading : PassportContent
    data object Unavailable : PassportContent

    @Immutable
    data class Ready(val stamps: List<PassportStampUi>, val districtCount: Int) : PassportContent
}

/** The Passport of one city. A null [state] is still loading. */
internal fun passportContent(
    state: PassportState?,
    cityId: String,
    districtNames: Map<String, String>,
    dateFormat: DateTimeFormatter,
): PassportContent = when (state) {
    null -> PassportContent.Loading
    PassportState.Unavailable -> PassportContent.Unavailable
    is PassportState.Loaded -> {
        val inCity = state.stamps.filter { it.cityId == cityId }
        PassportContent.Ready(
            stamps = inCity.map { it.toUi(districtNames, dateFormat) },
            districtCount = inCity.distinctBy { it.districtId }.size,
        )
    }
}

private fun PassportStamp.toUi(districtNames: Map<String, String>, dateFormat: DateTimeFormatter) = PassportStampUi(
    key = key,
    initials = stampInitials(name),
    name = name,
    district = districtNames[districtId] ?: districtId,
    date = date.format(dateFormat),
    type = stampStopType(kind, category),
    shape = stampShapeFor(key),
)

internal fun stampStopType(kind: StampKind, category: String?): StopType = when (kind) {
    StampKind.Event -> eventStopType(category)
    StampKind.Meal -> if (category == "bar") StopType.Bar else StopType.Meal
}

/**
 * The playground's six Material shapes, with their sizes in dp. Constant names are not stored: a
 * Stamp's shape is always worked out from its key.
 */
enum class StampShape(val widthDp: Int, val heightDp: Int) {
    VerySunny(180, 180),
    Cookie9Sided(160, 160),
    Cookie7Sided(100, 100),
    RoundedLarge(150, 80),
    RoundedMedium(160, 80),
    Circle(110, 110),
}

/**
 * The same key always gets the same shape, on every launch. [String.hashCode] is fixed by the
 * language, and the extra mixing spreads keys that differ only by a few digits across the set.
 */
internal fun stampShapeFor(key: String): StampShape {
    var hash = key.hashCode()
    hash = (hash xor (hash ushr 16)) * -0x7a143595
    hash = (hash xor (hash ushr 13)) * -0x3d4d51cb
    hash = hash xor (hash ushr 16)
    return StampShape.entries[Math.floorMod(hash, StampShape.entries.size)]
}
