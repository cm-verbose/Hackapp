package com.example.hackapp.ui.components

/** The page nearest to [aroundPage] that shows [value] on a looping wheel of [count] values. */
internal fun wheelPageFor(value: Int, count: Int, aroundPage: Int): Int {
    val base = aroundPage - Math.floorMod(aroundPage, count) + value
    return listOf(base - count, base, base + count).minBy { kotlin.math.abs(it - aroundPage) }
}

/** The typed number, or null when it is not a valid value for a wheel of [count] values. */
internal fun parseTimeUnit(text: String, count: Int): Int? =
    text.toIntOrNull()?.takeIf { it in 0 until count }

internal fun twoDigits(value: Int): String = value.toString().padStart(2, '0')
