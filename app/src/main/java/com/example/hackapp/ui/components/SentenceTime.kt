package com.example.hackapp.ui.components

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Time of day as written inside a sentence: `19 h 15` or `20 h` in French (non-breaking spaces around the
 * `h`), `19:15` in English. Use it for every time placed in a string argument.
 */
fun formatSentenceTime(time: LocalTime, locale: Locale = Locale.getDefault()): String {
    val pattern = when {
        locale.language != "fr" -> "HH:mm"
        time.minute == 0 -> "H\u00A0'h'"
        else -> "H\u00A0'h'\u00A0mm"
    }
    return DateTimeFormatter.ofPattern(pattern, locale).format(time)
}
