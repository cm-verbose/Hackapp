package com.example.hackapp.scaffold.screens.share

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleStartEffect
import com.example.hackapp.R
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.scaffold.sandbox.share.NearbyOfferWatcher
import com.example.hackapp.scaffold.sandbox.share.NearbyPresence
import com.example.hackapp.scaffold.sandbox.share.ShareOffer

/**
 * Tells the person when a phone nearby is on a share page, wanting to send them a Stamp or an
 * Itinerary, and opens the matching receive page. Place it once, over the whole app.
 *
 * Listening needs the Nearby permissions; a phone that never granted them is not told.
 */
@Composable
fun NearbyOfferPrompt(onReceive: (ShareOffer) -> Unit) {
    val context = LocalContext.current.applicationContext
    val auth = LocalAuthService.current
    val watcher = remember { NearbyOfferWatcher(context) }
    DisposableEffect(watcher) {
        NearbyPresence.watcher = watcher
        onDispose { if (NearbyPresence.watcher === watcher) NearbyPresence.watcher = null }
    }
    LifecycleStartEffect(watcher) {
        watcher.start()
        onStopOrDispose { watcher.stop() }
    }
    // Each share is shown once. Its name has a code that is new every time the other phone starts
    // sharing, so the next press and hold there asks again.
    var handled by remember { mutableStateOf(emptySet<String>()) }
    val offer = watcher.offers.values.firstOrNull { it.deviceName !in handled }
        ?.takeIf { auth.currentUser != null }
        ?: return
    val name = readableDeviceName(offer.deviceName)
    AlertDialog(
        onDismissRequest = { handled = handled + offer.deviceName },
        title = {
            Text(
                stringResource(
                    when (offer.offer) {
                        ShareOffer.Stamp -> R.string.share_offer_stamp
                        ShareOffer.Itinerary -> R.string.share_offer_itinerary
                    },
                    name,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                handled = handled + offer.deviceName
                onReceive(offer.offer)
            }) { Text(stringResource(R.string.share_offer_receive)) }
        },
        dismissButton = {
            TextButton(onClick = { handled = handled + offer.deviceName }) { Text(stringResource(R.string.share_offer_later)) }
        },
    )
}
