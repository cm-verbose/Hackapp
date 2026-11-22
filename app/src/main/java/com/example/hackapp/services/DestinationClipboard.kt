package com.example.hackapp.services

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.widget.Toast
import com.example.hackapp.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

private const val GEOCODE_TIMEOUT_MILLIS = 1_500L

/**
 * Chrono takes no destination from other apps: it has no directions link and ignores search
 * intents (checked on Chrono's intent filters, 2026-11-22). So the Stop's street address, or its
 * name when no address is found, goes on the clipboard first, ready to paste into "Plan a trip".
 */
suspend fun copyDestinationForTransit(context: Context, destination: HandoffDestination) {
    val app = context.applicationContext
    val address = try {
        withTimeoutOrNull(GEOCODE_TIMEOUT_MILLIS) { streetAddress(app, destination) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
    val text = address ?: destination.name
    withContext(Dispatchers.Main) {
        val clipboard = app.getSystemService(ClipboardManager::class.java) ?: return@withContext
        clipboard.setPrimaryClip(ClipData.newPlainText(destination.name, text))
        Toast.makeText(app, app.getString(R.string.handoff_address_copied, text), Toast.LENGTH_LONG).show()
    }
}

private suspend fun streetAddress(context: Context, destination: HandoffDestination): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.CANADA_FRENCH)
    val lat = destination.location.lat
    val lng = destination.location.lng
    val result = if (Build.VERSION.SDK_INT >= 33) {
        suspendCancellableCoroutine { cont ->
            geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<android.location.Address>) { cont.resume(addresses.firstOrNull()) }
                override fun onError(errorMessage: String?) { cont.resume(null) }
            })
        }
    } else {
        withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
        }
    }
    val street = listOfNotNull(result?.subThoroughfare, result?.thoroughfare).joinToString(" ").ifBlank { null }
    return street?.let { listOfNotNull(it, result?.locality).joinToString(", ") }
}
