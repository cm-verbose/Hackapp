package com.example.hackapp.data

import java.time.DateTimeException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** The bundled data mixes `-05:00` and `-0500` offsets, and both are usable. Seconds are optional. */
private val EventTimestamp = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm[:ss][XXX][XX]")

/** Null when [text] is missing or is not a full timestamp with an offset. */
fun parseEventTime(text: String?): OffsetDateTime? {
    if (text.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(text.trim(), EventTimestamp)
    } catch (_: DateTimeException) {
        null
    }
}
