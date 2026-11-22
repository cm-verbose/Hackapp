package com.example.hackapp.itinerary

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

const val MIN_TICKET_PRICE_CAD = 0.0
const val MAX_TICKET_PRICE_CAD = 500.0

fun isValidTicketPrice(priceCad: Double): Boolean =
    priceCad.isFinite() && priceCad >= MIN_TICKET_PRICE_CAD && priceCad <= MAX_TICKET_PRICE_CAD

/** The middle value, or the mean of the two middle values. Null when there are no values. */
fun medianOf(values: Collection<Double>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

/** What others may see of the reports. It never says who submitted them. */
data class TicketPriceSummary(val medianCad: Double, val reportCount: Int)

/**
 * User-reported ticket prices, one editable report per user and [ShowtimeKey]. A key covers all
 * showtimes of the same title and venue on the same day.
 */
interface TicketPriceRepository {
    fun observeSummary(key: ShowtimeKey): Flow<TicketPriceSummary?>
    suspend fun getSummary(key: ShowtimeKey): TicketPriceSummary?
    suspend fun getOwnReport(uid: String, key: ShowtimeKey): Double?
    /** Creates or replaces this person's report. Returns false, storing nothing, if out of range. */
    suspend fun saveReport(uid: String, key: ShowtimeKey, priceCad: Double): Boolean
    suspend fun deleteReport(uid: String, key: ShowtimeKey)
}

/** Keeps reports for this process only, until the Firestore implementation exists. */
class InMemoryTicketPriceRepository : TicketPriceRepository {
    private val reports = MutableStateFlow<Map<String, Map<String, Double>>>(emptyMap())

    override fun observeSummary(key: ShowtimeKey): Flow<TicketPriceSummary?> =
        reports.map { summaryOf(it[key.value]) }.distinctUntilChanged()

    override suspend fun getSummary(key: ShowtimeKey): TicketPriceSummary? =
        summaryOf(reports.value[key.value])

    override suspend fun getOwnReport(uid: String, key: ShowtimeKey): Double? =
        reports.value[key.value]?.get(uid)

    override suspend fun saveReport(uid: String, key: ShowtimeKey, priceCad: Double): Boolean {
        if (!isValidTicketPrice(priceCad)) return false
        reports.update { all -> all + (key.value to (all[key.value].orEmpty() + (uid to priceCad))) }
        return true
    }

    override suspend fun deleteReport(uid: String, key: ShowtimeKey) {
        reports.update { all ->
            val remaining = all[key.value].orEmpty() - uid
            if (remaining.isEmpty()) all - key.value else all + (key.value to remaining)
        }
    }

    private fun summaryOf(byUser: Map<String, Double>?): TicketPriceSummary? {
        val values = byUser?.values ?: return null
        return medianOf(values)?.let { TicketPriceSummary(it, values.size) }
    }
}
