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
    shape = shapeSeed?.let(::stampShapeForSeed) ?: stampShapeFor(key),
)

internal fun stampStopType(kind: StampKind, category: String?): StopType = when (kind) {
    StampKind.Event -> eventStopType(category)
    StampKind.Meal -> if (category == "bar") StopType.Bar else StopType.Meal
}

/**
 * Whether the page is too short to keep a usable field on screen and must scroll. Counts the page
 * padding and gaps, the optional city selector, the hero at this text size and a 200 dp field.
 */
internal fun passportNeedsScroll(heightDp: Float, fontScale: Float, hasCitySelector: Boolean): Boolean {
    val selector = if (hasCitySelector) 64f else 0f
    val hero = 52f + 72f * fontScale
    return heightDp < 16f + selector + hero + 16f + 200f + 16f
}

/**
 * How much to enlarge initials inside a Stamp drawn at [itemScale], so they never look smaller
 * than label large (14 sp, against title medium's 16 sp) and still follow the system text size.
 */
internal fun readableInitialsScale(itemScale: Float): Float = maxOf(1f, 0.875f / itemScale)

/**
 * The playground's six Material shapes, with their sizes in dp. Constant names are not stored: a
 * Stamp's shape is worked out from its random seed, or from its key for older Stamps.
 */
enum class StampShape(val widthDp: Int, val heightDp: Int) {
    VerySunny(180, 180),
    Cookie9Sided(160, 160),
    Cookie7Sided(100, 100),
    RoundedLarge(150, 80),
    RoundedMedium(160, 80),
    Circle(110, 110),
    ;

    /** False for the rounded rectangles, whose text room is a box rather than a circle. */
    val isRound: Boolean get() = this != RoundedLarge && this != RoundedMedium
}

/** The shape for a Stamp's random [PassportStamp.shapeSeed]. Shapes do not follow the Stop type. */
internal fun stampShapeForSeed(seed: Int): StampShape =
    StampShape.entries[Math.floorMod(seed, StampShape.entries.size)]

/**
 * Fallback for Stamps saved without a seed. The same key always gets the same shape, on every launch. [String.hashCode] is fixed by the
 * language, and the extra mixing spreads keys that differ only by a few digits across the set.
 */
internal fun stampShapeFor(key: String): StampShape {
    var hash = key.hashCode()
    hash = (hash xor (hash ushr 16)) * -0x7a143595
    hash = (hash xor (hash ushr 13)) * -0x3d4d51cb
    hash = hash xor (hash ushr 16)
    return StampShape.entries[Math.floorMod(hash, StampShape.entries.size)]
}
