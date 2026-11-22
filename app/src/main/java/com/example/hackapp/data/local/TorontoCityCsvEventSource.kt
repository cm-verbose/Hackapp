package com.example.hackapp.data.local

import android.content.Context
import android.util.Log
import com.example.hackapp.data.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Static Toronto city events, bundled as `assets/events_toronto_city.csv`.
 * Exposes the same `suspend fetchEvents()` shape as the remote sources so it
 * can be swapped for a real API later without touching the ViewModel.
 */
class TorontoCityCsvEventSource(
    context: Context,
    private val assetName: String = ASSET_NAME,
) {
    private val appContext = context.applicationContext

    suspend fun fetchEvents(): List<Event> = withContext(Dispatchers.IO) {
        val text = appContext.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val rows = CsvParser.parseWithHeader(text)
        val events = rows.mapNotNull { it.toEventOrNull(defaultCity = "Toronto", defaultTimezone = "America/Toronto") }
        if (events.size != rows.size) {
            Log.w(TAG, "Skipped ${rows.size - events.size} CSV rows missing id/title/startDate")
        }
        events
    }

    private companion object {
        const val ASSET_NAME = "events_toronto_city.csv"
        const val TAG = "TorontoCityCsv"
    }
}
