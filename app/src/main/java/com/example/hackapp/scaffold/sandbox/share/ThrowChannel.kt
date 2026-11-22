package com.example.hackapp.scaffold.sandbox.share

import kotlinx.coroutines.flow.SharedFlow

/**
 * What the physics sandbox needs to throw an object to a nearby device. [NearbyShare] is the real
 * one. A later Itinerary share can carry its data in [ShareMessage.Throw.payload].
 */
interface ThrowChannel {
    /** Connected devices by endpoint id. */
    val peers: Map<String, SharePeer>
    val incomingThrows: SharedFlow<IncomingThrow>

    fun broadcastHeading(forwardBearing: Float?)
    fun sendThrow(endpointId: String, message: ShareMessage.Throw)
}
