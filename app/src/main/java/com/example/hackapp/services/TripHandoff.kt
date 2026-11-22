package com.example.hackapp.services

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.hackapp.citypack.FareTable
import com.example.hackapp.citypack.RideHailConfig
import com.example.hackapp.citypack.TransitAppConfig
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.data.model.LocalizedText
import java.net.URLEncoder

/** The next Stop, as handed to an external travel app. */
data class HandoffDestination(val name: String, val location: LatLng)

enum class HandoffResult { OPENED_APP, OPENED_FALLBACK, UNAVAILABLE }

data class Fare(val label: LocalizedText, val agencyName: String)

/** What a Leg costs by public transit in this city. */
fun interface FareService {
    fun fareForLeg(): Fare
}

/** Sends a person to the city's public transit option. */
interface TransitHandoff {
    val appName: String
    /** Opens the Transit App, or transit directions if it is unavailable. */
    fun openTransitApp(destination: HandoffDestination): HandoffResult
    fun openDirections(destination: HandoffDestination): HandoffResult
}

/** Sends a person to a ride-hailing app with the next Stop as the drop-off. */
interface RideHailHandoff {
    val appName: String
    fun requestRide(destination: HandoffDestination): HandoffResult
}

class FareTableService(private val table: FareTable) : FareService {
    override fun fareForLeg() = Fare(table.singleRide, table.agencyName)
}

/** External URLs, kept free of Android types so they can be unit-tested. */
internal object HandoffUrls {
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun transitDirections(destination: LatLng): String =
        "https://www.google.com/maps/dir/?api=1" +
            "&destination=${destination.lat},${destination.lng}&travelmode=transit"

    fun uberDropoff(destination: HandoffDestination): String =
        "https://m.uber.com/ul/?action=setPickup&pickup=my_location" +
            "&dropoff%5Blatitude%5D=${destination.location.lat}" +
            "&dropoff%5Blongitude%5D=${destination.location.lng}" +
            "&dropoff%5Bnickname%5D=${encode(destination.name)}"
}

class AndroidTransitHandoff(
    context: Context,
    private val config: TransitAppConfig,
) : TransitHandoff {
    private val appContext = context.applicationContext

    override val appName: String get() = config.displayName

    override fun openTransitApp(destination: HandoffDestination): HandoffResult {
        val launch = config.packageName?.let(appContext.packageManager::getLaunchIntentForPackage)
        if (launch != null && appContext.startSafely(launch)) return HandoffResult.OPENED_APP
        return if (openDirections(destination) == HandoffResult.OPENED_APP) {
            HandoffResult.OPENED_FALLBACK
        } else {
            HandoffResult.UNAVAILABLE
        }
    }

    override fun openDirections(destination: HandoffDestination): HandoffResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(HandoffUrls.transitDirections(destination.location)))
        return if (appContext.startSafely(intent)) HandoffResult.OPENED_APP else HandoffResult.UNAVAILABLE
    }
}

class UberRideHailHandoff(
    context: Context,
    private val config: RideHailConfig,
) : RideHailHandoff {
    private val appContext = context.applicationContext

    override val appName: String get() = config.displayName

    /** The universal link opens the Uber app when installed, otherwise Uber on the web. */
    override fun requestRide(destination: HandoffDestination): HandoffResult {
        val uri = Uri.parse(HandoffUrls.uberDropoff(destination))
        val inApp = Intent(Intent.ACTION_VIEW, uri).setPackage(config.packageName)
        if (appContext.startSafely(inApp)) return HandoffResult.OPENED_APP
        return if (appContext.startSafely(Intent(Intent.ACTION_VIEW, uri))) {
            HandoffResult.OPENED_FALLBACK
        } else {
            HandoffResult.UNAVAILABLE
        }
    }
}

private fun Context.startSafely(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}
