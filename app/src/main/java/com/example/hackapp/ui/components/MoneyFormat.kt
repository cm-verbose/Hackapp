package com.example.hackapp.ui.components

import java.text.NumberFormat
import java.util.Locale

private const val NBSP = ' '
private const val EN_DASH = '–'

private fun Locale.isFrench() = language == "fr"

private fun cadNumber(amount: Double, locale: Locale): String {
    val whole = amount % 1.0 == 0.0
    return NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        minimumFractionDigits = if (whole) 0 else 2
        maximumFractionDigits = if (whole) 0 else 2
    }.format(amount)
}

/** `25 $` / `12,50 $` in French (non-breaking space), `$25` / `$12.50` in English. */
fun formatCad(amount: Double, locale: Locale = Locale.getDefault()): String {
    val number = cadNumber(amount, locale)
    return if (locale.isFrench()) "$number$NBSP\$" else "\$$number"
}

fun formatCad(amount: Int, locale: Locale = Locale.getDefault()): String = formatCad(amount.toDouble(), locale)

/**
 * A price range as a range: `10–20 $` / `$10–20`. A null [max] is the open bracket, written
 * `100 $ +` / `$100+` like the `budget_100_plus` bracket label. Equal bounds show one amount.
 */
fun formatCadRange(min: Double, max: Double?, locale: Locale = Locale.getDefault()): String {
    val low = cadNumber(min, locale)
    val french = locale.isFrench()
    return when {
        max == null -> if (french) "$low$NBSP\$$NBSP+" else "\$$low+"
        max == min -> formatCad(min, locale)
        else -> {
            val high = cadNumber(max, locale)
            if (french) "$low$EN_DASH$high$NBSP\$" else "\$$low$EN_DASH$high"
        }
    }
}

fun formatCadRange(min: Int, max: Int?, locale: Locale = Locale.getDefault()): String =
    formatCadRange(min.toDouble(), max?.toDouble(), locale)

/**
 * A duration as written in a sentence: `2 h`, `45 min`, and `1 h 30` in French or `1 h 30 min`
 * in English, with non-breaking spaces.
 */
fun formatDurationMinutes(minutes: Int, locale: Locale = Locale.getDefault()): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$rest${NBSP}min"
        rest == 0 -> "$hours${NBSP}h"
        locale.isFrench() -> "$hours${NBSP}h$NBSP${rest.toString().padStart(2, '0')}"
        else -> "$hours${NBSP}h$NBSP$rest${NBSP}min"
    }
}
