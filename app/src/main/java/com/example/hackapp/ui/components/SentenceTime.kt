package com.example.hackapp.ui.components

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Time of day as written inside a sentence: `19 h 15` in French (non-breaking spaces around the
 * `h`), `19:15` in English. Use it for every time placed in a string argument.
 */
fun formatSentenceTime(time: LocalTime, locale: Locale = Locale.getDefault()): String {
    val pattern = if (locale.language == "fr") "H 'h' mm" else "HH:mm"
    return DateTimeFormatter.ofPattern(pattern, locale).format(time)
}
