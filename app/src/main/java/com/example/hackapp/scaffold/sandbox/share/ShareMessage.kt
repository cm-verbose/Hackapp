package com.example.hackapp.scaffold.sandbox.share

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Messages exchanged between devices over Nearby Connections. */
@Serializable
sealed interface ShareMessage {

    /** The sender's [ThrowGeometry.forwardBearing], or `null` without a compass. */
    @Serializable
    @SerialName("heading")
    data class Heading(val forwardBearing: Float?) : ShareMessage

    /**
     * Out-of-band UWB setup. UWB can't discover peers by itself, so the controller sends its
     * channel and session parameters and the controlee answers with its address.
     */
    @Serializable
    @SerialName("uwb")
    data class UwbHello(
        val address: String,
        val isController: Boolean,
        val channel: Int? = null,
        val preambleIndex: Int? = null,
        val sessionId: Int? = null,
        val sessionKeyHex: String? = null,
    ) : ShareMessage

    /**
     * A shape thrown to the receiving device.
     *
     * Shape content is a composable lambda and can't be sent, but every device runs the same
     * app with the same item list, so the item's index identifies it.
     */
    @Serializable
    @SerialName("throw")
    data class Throw(
        val itemIndex: Int,
        /** World bearing the shape travels along, or `null` without a compass. */
        val bearing: Float?,
        /** Speed in meters per second of the simulated world. */
        val speed: Float,
        val angularVelocity: Float,
        /** Opaque content carried with the object, such as a shared Itinerary. Stamps are never sent. */
        val payload: String? = null,
    ) : ShareMessage

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(message: ShareMessage): ByteArray =
            json.encodeToString(serializer(), message).encodeToByteArray()

        fun decode(bytes: ByteArray): ShareMessage? =
            runCatching { json.decodeFromString(serializer(), bytes.decodeToString()) }.getOrNull()
    }
}
