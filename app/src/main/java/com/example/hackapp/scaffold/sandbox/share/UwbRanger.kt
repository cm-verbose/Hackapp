package com.example.hackapp.scaffold.sandbox.share

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.uwb.RangingParameters
import androidx.core.uwb.RangingResult
import androidx.core.uwb.UwbAddress
import androidx.core.uwb.UwbClientSessionScope
import androidx.core.uwb.UwbComplexChannel
import androidx.core.uwb.UwbControllerSessionScope
import androidx.core.uwb.UwbDevice
import androidx.core.uwb.UwbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val TAG = "UwbRanger"

/**
 * Measures the direction to each connected peer with Ultra-Wideband, on devices that have it.
 *
 * Every pair of devices runs its own unicast session. UWB can't find peers by itself, so
 * session setup goes through Nearby Connections ([send]): the controller shares its channel,
 * session id and key, and the controlee shares its address.
 *
 * Must be used from the main thread.
 */
class UwbRanger(
    private val context: Context,
    private val scope: CoroutineScope,
    private val send: (endpointId: String, hello: ShareMessage.UwbHello) -> Unit,
    private val onPosition: (endpointId: String, azimuthDeg: Float, elevationDeg: Float?) -> Unit,
) {
    private class Session(val isController: Boolean) {
        var local: UwbClientSessionScope? = null
        var sessionId = 0
        var sessionKey = ByteArray(0)
        var peerHello: ShareMessage.UwbHello? = null
        var job: Job? = null
        var setupJob: Job? = null
    }

    private val manager: UwbManager? by lazy {
        if (isSupported(context)) UwbManager.createInstance(context) else null
    }
    private val sessions = mutableMapOf<String, Session>()

    /** Starts UWB setup with a newly connected peer. Exactly one side must be the controller. */
    fun onPeerConnected(endpointId: String, isController: Boolean) {
        val manager = manager ?: return
        onPeerDisconnected(endpointId)
        val session = Session(isController)
        sessions[endpointId] = session

        session.setupJob = scope.launch {
            try {
                if (!manager.isAvailable()) return@launch
                val local = if (isController) {
                    manager.controllerSessionScope()
                } else {
                    manager.controleeSessionScope()
                }
                session.local = local
                val hello = if (local is UwbControllerSessionScope) {
                    session.sessionId = Random.nextInt()
                    session.sessionKey = Random.nextBytes(8)
                    ShareMessage.UwbHello(
                        address = local.localAddress.toString(),
                        isController = true,
                        channel = local.uwbComplexChannel.channel,
                        preambleIndex = local.uwbComplexChannel.preambleIndex,
                        sessionId = session.sessionId,
                        sessionKeyHex = session.sessionKey.toHex(),
                    )
                } else {
                    ShareMessage.UwbHello(address = local.localAddress.toString(), isController = false)
                }
                send(endpointId, hello)
                tryStartRanging(endpointId, session)
            } catch (e: Exception) {
                Log.w(TAG, "UWB setup failed for $endpointId", e)
            }
        }
    }

    fun onHello(endpointId: String, hello: ShareMessage.UwbHello) {
        val session = sessions[endpointId] ?: return
        // Both sides picked the same role; this pair falls back to the compass.
        if (hello.isController == session.isController) return
        session.peerHello = hello
        tryStartRanging(endpointId, session)
    }

    fun onPeerDisconnected(endpointId: String) {
        sessions.remove(endpointId)?.let {
            it.setupJob?.cancel()
            it.job?.cancel()
        }
    }

    fun stop() {
        sessions.keys.toList().forEach(::onPeerDisconnected)
    }

    /** Starts ranging once both our session scope and the peer's hello are ready. */
    private fun tryStartRanging(endpointId: String, session: Session) {
        val local = session.local ?: return
        val peer = session.peerHello ?: return
        if (session.job != null) return

        val params = try {
            if (session.isController) {
                val controller = local as UwbControllerSessionScope
                rangingParameters(
                    sessionId = session.sessionId,
                    key = session.sessionKey,
                    channel = controller.uwbComplexChannel,
                    peerAddress = peer.address,
                )
            } else {
                rangingParameters(
                    sessionId = peer.sessionId ?: return,
                    key = peer.sessionKeyHex?.hexToBytes() ?: return,
                    channel = UwbComplexChannel(
                        channel = peer.channel ?: return,
                        preambleIndex = peer.preambleIndex ?: return,
                    ),
                    peerAddress = peer.address,
                )
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid UWB parameters from $endpointId", e)
            return
        }

        session.job = scope.launch {
            try {
                local.prepareSession(params).collect { result ->
                    when (result) {
                        is RangingResult.RangingResultPosition -> {
                            val azimuth = result.position.azimuth?.value ?: return@collect
                            onPosition(endpointId, azimuth, result.position.elevation?.value)
                        }
                        is RangingResult.RangingResultPeerDisconnected,
                        is RangingResult.RangingResultFailure ->
                            Log.d(TAG, "UWB ranging with $endpointId stopped: $result")
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "UWB ranging failed for $endpointId", e)
            }
        }
    }

    private fun rangingParameters(
        sessionId: Int,
        key: ByteArray,
        channel: UwbComplexChannel,
        peerAddress: String,
    ) = RangingParameters(
        uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
        sessionId = sessionId,
        subSessionId = 0,
        sessionKeyInfo = key,
        subSessionKeyInfo = null,
        complexChannel = channel,
        peerDevices = listOf(UwbDevice(UwbAddress(peerAddress))),
        updateRateType = RangingParameters.RANGING_UPDATE_RATE_FREQUENT,
    )

    companion object {
        /** Whether this device has UWB hardware and the app may use it. */
        fun isSupported(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_UWB) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.UWB_RANGING) ==
                PackageManager.PERMISSION_GRANTED
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToBytes(): ByteArray? {
    if (length % 2 != 0) return null
    return runCatching { chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull()
}
