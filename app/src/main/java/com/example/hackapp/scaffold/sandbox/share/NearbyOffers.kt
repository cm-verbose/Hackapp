package com.example.hackapp.scaffold.sandbox.share

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Strategy

private const val TAG = "NearbyOffers"

/** What a phone on a share page is offering, carried in its advertised Nearby name. */
enum class ShareOffer(val tag: String) {
    Stamp("stamp"),
    Itinerary("itinerary"),
    ;

    companion object {
        private const val SEPARATOR = "~"

        /** The advertised name: [name] plus the offer, so phones that are only listening can tell. */
        fun advertisedName(name: String, offer: ShareOffer?): String =
            if (offer == null) name else "$name$SEPARATOR${offer.tag}"

        fun of(advertisedName: String): ShareOffer? =
            advertisedName.substringAfterLast(SEPARATOR, "").let { tag -> entries.firstOrNull { it.tag == tag } }

        /** The device part of an advertised name, without the offer. */
        fun deviceName(advertisedName: String): String = advertisedName.substringBeforeLast(SEPARATOR)
    }
}

/** A nearby phone that is waiting to send something. */
data class NearbyOffer(val endpointId: String, val deviceName: String, val offer: ShareOffer)

/**
 * While the app is open, listens (discovery only, nothing advertised) for phones that are on a
 * share page, so the person can be told someone wants to send them something. It never asks for
 * permissions: without them it simply does not run.
 *
 * Nearby has one connections client per app, so the watcher pauses while a share page runs its
 * own [NearbyShare] (see [NearbyPresence]). Must be used from the main thread.
 */
class NearbyOfferWatcher(private val context: Context) {
    private val client = Nearby.getConnectionsClient(context)
    private var wanted = false
    private var running = false

    /** Offers in range, by endpoint id. */
    val offers = mutableStateMapOf<String, NearbyOffer>()

    private val callback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val offer = ShareOffer.of(info.endpointName) ?: return
            offers[endpointId] = NearbyOffer(endpointId, ShareOffer.deviceName(info.endpointName), offer)
        }

        override fun onEndpointLost(endpointId: String) {
            offers.remove(endpointId)
        }
    }

    /** Listens while the app is in the foreground. */
    fun start() {
        wanted = true
        update()
    }

    fun stop() {
        wanted = false
        update()
    }

    /** Called by [NearbyPresence] when a share page starts or stops. */
    internal fun update() {
        val shouldRun = wanted && NearbyPresence.activeShares == 0 && NearbyShare.hasRequiredPermissions(context) &&
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        if (shouldRun == running) return
        running = shouldRun
        if (shouldRun) {
            client.startDiscovery(
                SERVICE_ID,
                callback,
                DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build(),
            ).addOnFailureListener { Log.d(TAG, "Listening for offers failed: ${it.message}") }
        } else {
            offers.clear()
            // A share page restarts discovery itself right after this.
            client.stopDiscovery()
        }
    }
}

/**
 * Counts the share pages running Nearby, so the app-wide [NearbyOfferWatcher] steps aside for
 * them. Main thread only.
 */
object NearbyPresence {
    var watcher: NearbyOfferWatcher? = null
    var activeShares = 0
        private set

    /** Call before a share page starts Nearby: the watcher stops at once, not later. */
    fun acquire() {
        activeShares++
        watcher?.update()
    }

    fun release() {
        if (activeShares > 0) activeShares--
        watcher?.update()
    }
}
