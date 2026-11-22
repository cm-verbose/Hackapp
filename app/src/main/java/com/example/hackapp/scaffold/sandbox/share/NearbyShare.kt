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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID
import kotlin.math.abs

private const val TAG = "NearbyShare"
private const val SERVICE_ID = "com.example.hackapp.sandbox.share"

/** UWB readings older than this are ignored when picking a target. */
private const val UWB_FRESH_MS = 1_500L

/** A shape thrown to this device by the peer named [fromName]. */
data class IncomingThrow(val fromName: String, val message: ShareMessage.Throw)

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
 * a pair requesting at once, only the device with the smaller random name requests; the same
 * comparison decides who is the UWB controller. Connections are accepted without a
 * confirmation prompt.
 *
 * Must be used from the main thread.
 */
class NearbyShare(private val context: Context, scope: CoroutineScope) {

    private val client = Nearby.getConnectionsClient(context)
    /**
     * Advertised name: the device model for readable messages, plus a random suffix that keeps
     * names unique and decides which side of a pair requests the connection.
     */
    private val localName = "${Build.MODEL} #${UUID.randomUUID().toString().take(4)}"
    private var running = false
    private var lastHeading: ShareMessage.Heading? = null

    /**
     * Why advertising or discovery isn't running, for display; `null` while both work.
     * Without this, a failure (such as a missing permission) only shows up in logcat.
     */
    var error by mutableStateOf<String?>(null)
        private set

    /** Connected peers by endpoint id. */
    val peers = mutableStateMapOf<String, SharePeer>()

    private val _incomingThrows = MutableSharedFlow<IncomingThrow>(extraBufferCapacity = 16)
    val incomingThrows: SharedFlow<IncomingThrow> = _incomingThrows

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

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            when (val message = ShareMessage.decode(bytes)) {
                is ShareMessage.Heading -> peers[endpointId]?.let {
                    peers[endpointId] = it.copy(forwardBearing = message.forwardBearing)
                }
                is ShareMessage.UwbHello -> uwb.onHello(endpointId, message)
                is ShareMessage.Throw -> {
                    val from = peers[endpointId]?.name ?: "a nearby device"
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
            if (!resolution.status.isSuccess) {
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
            if (info.serviceId != SERVICE_ID || endpointId in peers) return
            if (localName >= info.endpointName) return // The other device requests.
            client.requestConnection(localName, endpointId, connectionCallback)
                .addOnFailureListener { Log.d(TAG, "Request to $endpointId failed", it) }
        }

        override fun onEndpointLost(endpointId: String) {}
    }

    fun start() {
        if (running) return
        running = true
        val strategy = Strategy.P2P_CLUSTER
        client.startAdvertising(
            localName,
            SERVICE_ID,
            connectionCallback,
            AdvertisingOptions.Builder().setStrategy(strategy).build(),
        ).addOnFailureListener { onStartFailed("Advertising", it) }
        client.startDiscovery(
            SERVICE_ID,
            discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(strategy).build(),
        ).addOnFailureListener { onStartFailed("Discovery", it) }
    }

    private fun onStartFailed(what: String, e: Exception) {
        Log.w(TAG, "$what failed", e)
        val reason = (e as? ApiException)
            ?.let { ConnectionsStatusCodes.getStatusCodeString(it.statusCode) }
            ?: e.message
            ?: e.javaClass.simpleName
        error = "$what failed: $reason"
    }

    fun stop() {
        if (!running) return
        running = false
        uwb.stop()
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        peers.clear()
        pendingNames.clear()
        error = null
    }

    /** Shares this device's forward bearing, skipping updates too small to matter. */
    fun broadcastHeading(forwardBearing: Float?) {
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

    fun sendThrow(endpointId: String, message: ShareMessage.Throw) = send(endpointId, message)

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
 * Requests the needed permissions, then keeps a [NearbyShare] running while in composition.
 * Returns `null` while permissions are missing.
 */
@Composable
fun rememberNearbyShare(enabled: Boolean = true): NearbyShare? {
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

    val share = remember(enabled, granted) {
        if (enabled && granted) NearbyShare(context.applicationContext, scope) else null
    }
    DisposableEffect(share) {
        share?.start()
        onDispose { share?.stop() }
    }
    return share
}
