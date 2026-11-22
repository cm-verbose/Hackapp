package com.example.hackapp.scaffold.sandbox.share

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID
import kotlin.math.abs

private const val TAG = "NearbyShare"
internal const val SERVICE_ID = "com.example.hackapp.sandbox.share"
private val STRATEGY = Strategy.P2P_CLUSTER

/** UWB readings older than this are ignored when picking a target. */
private const val UWB_FRESH_MS = 1_500L

/** Delay before retrying advertising or discovery after a failure. */
private const val START_RETRY_MS = 5_000L

/** How often discovered but unconnected devices are (re)tried. */
private const val CONNECT_RETRY_MS = 4_000L

/**
 * How long the device with the larger name waits for the other side to request before it
 * requests itself. Bluetooth discovery is often one-sided, so the side that should request
 * may never see the other.
 */
private const val FALLBACK_REQUEST_MS = 8_000L

/** API level of Android 17, which introduced ACCESS_LOCAL_NETWORK. */
private const val ANDROID_17 = 37

/** A shape thrown to this device by the peer named [fromName]. */
data class IncomingThrow(val fromName: String?, val message: ShareMessage.Throw)

/** A connected nearby device. */
data class SharePeer(
    val endpointId: String,
    val name: String,
    val forwardBearing: Float? = null,
    val uwbAzimuthDeg: Float? = null,
    val uwbElevationDeg: Float? = null,
    val uwbAtMs: Long = 0L,
) {
    val hasFreshUwb: Boolean
        get() = uwbAzimuthDeg != null && SystemClock.elapsedRealtime() - uwbAtMs < UWB_FRESH_MS
}

/**
 * Keeps connections open to every nearby device running the sandbox, so throwing a shape is
 * instant (finding and connecting to a device takes a few seconds).
 *
 * Every device both advertises and discovers ([Strategy.P2P_CLUSTER]). To avoid both sides of
 * a pair requesting at once, the device with the smaller random name requests first; the
 * other side only requests if no connection appears within [FALLBACK_REQUEST_MS], since
 * discovery is often one-sided. Failed requests are retried every [CONNECT_RETRY_MS] while the
 * device stays discovered. The same name comparison decides who is the UWB controller.
 * Connections are accepted without a confirmation prompt.
 *
 * [offer] is what this page sends, added to the advertised name so phones that are only listening
 * ([NearbyOfferWatcher]) can tell their person. Null on a page that only receives.
 *
 * Must be used from the main thread.
 */
class NearbyShare(
    private val context: Context,
    private val scope: CoroutineScope,
    offer: ShareOffer? = null,
) : ThrowChannel {

    private val client = Nearby.getConnectionsClient(context)
    /**
     * Advertised name: the device model for readable messages, plus a random suffix that keeps
     * names unique and decides which side of a pair requests the connection.
     */
    private val localName = ShareOffer.advertisedName("${Build.MODEL} #${UUID.randomUUID().toString().take(4)}", offer)
    private var running = false
    private var lastHeading: ShareMessage.Heading? = null

    /**
     * Why advertising or discovery isn't running, for display; `null` while both work.
     * Without this, a failure (such as a missing permission) only shows up in logcat.
     */
    var error by mutableStateOf<String?>(null)
        private set

    private var advertisingError: String? = null
    private var discoveryError: String? = null
    private var retryJob: Job? = null

    /** Connected peers by endpoint id. */
    override val peers = mutableStateMapOf<String, SharePeer>()

    private val _incomingThrows = MutableSharedFlow<IncomingThrow>(extraBufferCapacity = 16)
    override val incomingThrows: SharedFlow<IncomingThrow> = _incomingThrows

    private val uwb = UwbRanger(
        context = context,
        scope = scope,
        send = { id, hello -> send(id, hello) },
        onPosition = { id, azimuth, elevation ->
            peers[id]?.let {
                peers[id] = it.copy(
                    uwbAzimuthDeg = azimuth,
                    uwbElevationDeg = elevation,
                    uwbAtMs = SystemClock.elapsedRealtime(),
                )
            }
        },
    )

    /** Endpoint names learnt while connecting, before the connection is established. */
    private val pendingNames = mutableMapOf<String, String>()

    /** A discovered device running the sandbox, which we may not be connected to yet. */
    private class Discovered(val name: String, val foundAtMs: Long) {
        var requesting = false
    }

    /** Devices currently in discovery range, by endpoint id. */
    private val discovered = mutableMapOf<String, Discovered>()
    private var connectJob: Job? = null

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            when (val message = ShareMessage.decode(bytes)) {
                is ShareMessage.Heading -> peers[endpointId]?.let {
                    peers[endpointId] = it.copy(forwardBearing = message.forwardBearing)
                }
                is ShareMessage.UwbHello -> uwb.onHello(endpointId, message)
                is ShareMessage.Throw -> {
                    val from = peers[endpointId]?.name
                    Log.d(TAG, "Received throw from $from: $message")
                    _incomingThrows.tryEmit(IncomingThrow(from, message))
                }
                null -> Log.w(TAG, "Unreadable payload from $endpointId")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            pendingNames[endpointId] = info.endpointName
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val name = pendingNames.remove(endpointId) ?: return
            discovered[endpointId]?.requesting = false
            if (!resolution.status.isSuccess) {
                // Retried by connectLoop while the device is still discovered.
                Log.d(TAG, "Connection to $endpointId failed: ${resolution.status}")
                return
            }
            peers[endpointId] = SharePeer(endpointId = endpointId, name = name)
            lastHeading?.let { send(endpointId, it) }
            uwb.onPeerConnected(endpointId, isController = localName < name)
        }

        override fun onDisconnected(endpointId: String) {
            peers.remove(endpointId)
            uwb.onPeerDisconnected(endpointId)
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            discovered[endpointId] = Discovered(info.endpointName, SystemClock.elapsedRealtime())
            maybeRequest(endpointId)
        }

        override fun onEndpointLost(endpointId: String) {
            discovered.remove(endpointId)
        }
    }

    /**
     * Requests a connection to a discovered device if we aren't connected or connecting, and
     * it's our turn: right away if our name is smaller, after [FALLBACK_REQUEST_MS] otherwise.
     */
    private fun maybeRequest(endpointId: String) {
        val device = discovered[endpointId] ?: return
        if (!running || endpointId in peers || endpointId in pendingNames || device.requesting) return
        val waited = SystemClock.elapsedRealtime() - device.foundAtMs
        if (localName >= device.name && waited < FALLBACK_REQUEST_MS) return

        device.requesting = true
        client.requestConnection(localName, endpointId, connectionCallback)
            .addOnFailureListener { e ->
                device.requesting = false
                // Both sides requested at once and the other one won: nothing to retry.
                if (e.statusCode() == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) return@addOnFailureListener
                Log.d(TAG, "Request to ${device.name} failed, will retry: ${e.message}")
            }
    }

    /** Keeps trying every discovered device we aren't connected to. */
    private fun startConnectLoop() {
        connectJob?.cancel()
        connectJob = scope.launch {
            while (running) {
                delay(CONNECT_RETRY_MS)
                discovered.keys.toList().forEach(::maybeRequest)
            }
        }
    }

    fun start() {
        if (running) return
        running = true
        NearbyPresence.acquire()

        val gms = GoogleApiAvailability.getInstance()
        val gmsStatus = gms.isGooglePlayServicesAvailable(context)
        if (gmsStatus != ConnectionResult.SUCCESS) {
            // Nearby Connections lives in Google Play services; without an up to date copy
            // every call fails with an opaque internal error.
            error = "Google Play services unavailable: ${gms.getErrorString(gmsStatus)} ($gmsStatus)"
            Log.w(TAG, error!!)
            return
        }

        // A previous session of this app (e.g. before a reinstall or process restart) can
        // still be registered with Play services and make new calls fail. Reset it first.
        client.stopAdvertising()
        client.stopDiscovery()
        startAdvertising()
        startDiscovery()
        startConnectLoop()
    }

    private fun startAdvertising() {
        client.startAdvertising(
            localName,
            SERVICE_ID,
            connectionCallback,
            AdvertisingOptions.Builder().setStrategy(STRATEGY).build(),
        )
            .addOnSuccessListener { advertisingError = null; updateError() }
            .addOnFailureListener { e ->
                if (e.statusCode() == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) return@addOnFailureListener
                advertisingError = describe("Advertising", e)
                updateError()
                scheduleRetry()
            }
    }

    private fun startDiscovery() {
        client.startDiscovery(
            SERVICE_ID,
            discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(STRATEGY).build(),
        )
            .addOnSuccessListener { discoveryError = null; updateError() }
            .addOnFailureListener { e ->
                if (e.statusCode() == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) return@addOnFailureListener
                discoveryError = describe("Discovery", e)
                updateError()
                scheduleRetry()
            }
    }

    /** Retries whichever of advertising and discovery failed, until both run or we stop. */
    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            delay(START_RETRY_MS)
            if (!running) return@launch
            if (advertisingError != null) {
                client.stopAdvertising()
                startAdvertising()
            }
            if (discoveryError != null) {
                client.stopDiscovery()
                startDiscovery()
            }
        }
    }

    private fun updateError() {
        error = listOfNotNull(advertisingError, discoveryError).joinToString("\n").ifEmpty { null }
    }

    private fun Exception.statusCode(): Int? = (this as? ApiException)?.statusCode

    /** e.g. "Advertising failed: STATUS_BLUETOOTH_ERROR (8007), retrying…". */
    private fun describe(what: String, e: Exception): String {
        Log.w(TAG, "$what failed", e)
        val reason = (e as? ApiException)?.let {
            val name = ConnectionsStatusCodes.getStatusCodeString(it.statusCode)
            val detail = it.status.statusMessage?.takeIf { msg -> msg.isNotBlank() && msg != name }
            "$name (${it.statusCode})" + (detail?.let { d -> ": $d" } ?: "")
        } ?: e.message ?: e.javaClass.simpleName
        return "$what failed: $reason, retrying…"
    }

    fun stop() {
        if (!running) return
        running = false
        retryJob?.cancel()
        connectJob?.cancel()
        advertisingError = null
        discoveryError = null
        uwb.stop()
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        peers.clear()
        pendingNames.clear()
        discovered.clear()
        error = null
        NearbyPresence.release()
    }

    /** Shares this device's forward bearing, skipping updates too small to matter. */
    override fun broadcastHeading(forwardBearing: Float?) {
        val previous = lastHeading?.forwardBearing
        val changed = when {
            lastHeading == null -> true
            previous == null || forwardBearing == null -> previous != forwardBearing
            else -> abs(ThrowGeometry.angleDiff(previous, forwardBearing)) >= 5f
        }
        if (!changed) return
        val message = ShareMessage.Heading(forwardBearing)
        lastHeading = message
        if (peers.isNotEmpty()) {
            client.sendPayload(peers.keys.toList(), Payload.fromBytes(ShareMessage.encode(message)))
        }
    }

    override fun sendThrow(endpointId: String, message: ShareMessage.Throw) = send(endpointId, message)

    private fun send(endpointId: String, message: ShareMessage) {
        client.sendPayload(endpointId, Payload.fromBytes(ShareMessage.encode(message)))
            .addOnFailureListener { Log.w(TAG, "Send to $endpointId failed", it) }
    }

    companion object {
        /** Runtime permissions Nearby Connections needs on this Android version. */
        fun requiredPermissions(): List<String> = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            // Android 17 made local network access a runtime permission. Play services needs
            // it for both advertising and discovery, but has no dedicated status code for it,
            // so a missing grant surfaces as a bare INTERNAL_ERROR (8).
            if (Build.VERSION.SDK_INT >= ANDROID_17) {
                add(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
            // Discovery still requires location on every Android version: Play services
            // refuses it with MISSING_PERMISSION_ACCESS_COARSE_LOCATION (8034) otherwise,
            // even on Android 13+ where NEARBY_WIFI_DEVICES exists. "Approximate" is enough.
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        /** Optional permissions: sharing works without them. */
        fun optionalPermissions(): List<String> = buildList {
            // Requested together with COARSE so the dialog offers precise or approximate
            // (Android 12+ requires asking for both). Precise isn't needed.
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            // Without UWB, throws are aimed with the compass only.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.UWB_RANGING)
        }

        fun hasRequiredPermissions(context: Context): Boolean = requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}

/**
 * Runs a [NearbyShare] while [enabled] and the page is started. Permissions are requested only
 * when [enabled] turns true, so pass true only after the person chose to share. Returns `null`
 * while permissions are missing. [offer] is what this page sends: nearby phones get a notice.
 */
@Composable
fun rememberNearbyShare(enabled: Boolean, offer: ShareOffer? = null): NearbyShare? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(NearbyShare.hasRequiredPermissions(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = NearbyShare.hasRequiredPermissions(context) }

    LaunchedEffect(enabled) {
        if (enabled && !granted) {
            launcher.launch(
                (NearbyShare.requiredPermissions() + NearbyShare.optionalPermissions()).toTypedArray()
            )
        }
    }

    val share = remember(enabled, granted, offer) {
        if (enabled && granted) NearbyShare(context.applicationContext, scope, offer) else null
    }
    LifecycleStartEffect(share) {
        granted = NearbyShare.hasRequiredPermissions(context)
        share?.start()
        onStopOrDispose { share?.stop() }
    }
    return share
}
