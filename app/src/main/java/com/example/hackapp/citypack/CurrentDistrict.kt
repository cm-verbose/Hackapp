package com.example.hackapp.citypack

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val LOCATION_TIMEOUT_MILLIS = 8_000L

fun Context.hasCoarseLocation(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * One rough position, from the network provider when available. Null without permission, with
 * location off, or after a few seconds without a fix. Never tracks the person.
 */
@SuppressLint("MissingPermission")
suspend fun Context.currentRoughLocation(): GeoPoint? {
    if (!hasCoarseLocation()) return null
    val manager = getSystemService(LocationManager::class.java) ?: return null
    if (!LocationManagerCompat.isLocationEnabled(manager)) return null
    val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
    val fix = withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(this@currentRoughLocation)) {
                continuation.resume(it)
            }
        }
    } ?: runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    return fix?.let { GeoPoint(it.latitude, it.longitude) }
}
