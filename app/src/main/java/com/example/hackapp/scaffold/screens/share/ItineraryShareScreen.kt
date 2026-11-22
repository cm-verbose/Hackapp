package com.example.hackapp.scaffold.screens.share

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.LocalItineraryRepository
import com.example.hackapp.itinerary.SharedItinerary
import com.example.hackapp.itinerary.activeStops
import com.example.hackapp.itinerary.decodeSharedItinerary
import com.example.hackapp.itinerary.importSharedItinerary
import com.example.hackapp.itinerary.toSharePayload
import com.example.hackapp.scaffold.sandbox.PhysicsScreen
import com.example.hackapp.scaffold.sandbox.SandboxItem
import com.example.hackapp.scaffold.sandbox.SandboxSharing
import com.example.hackapp.scaffold.sandbox.ThrowOutcome
import com.example.hackapp.scaffold.sandbox.share.rememberNearbyShare
import com.example.hackapp.scaffold.screens.itinerary.itineraryTitle
import com.example.hackapp.scaffold.screens.itinerary.loadDistrictNames
import com.example.hackapp.scaffold.screens.itinerary.openRoute
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.HackappTopBar
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant

private const val OWN_KEY = "own-itinerary"

/** What the last throw or receipt did, shown under the title. */
private sealed interface ShareStatus {
    data class Sent(val to: String?) : ShareStatus
    data object NoPeers : ShareStatus
    data object NoTarget : ShareStatus
    data object ReceiveFailed : ShareStatus
    data object SaveFailed : ShareStatus
}

/**
 * Flick an Itinerary to a phone nearby, through the Passport's physics and Nearby code. Both phones
 * open this page: the sender from its Itinerary, the receiver from Home. The receiver gets a copy
 * and decides whether to add it. With [itineraryId] null, the page only receives.
 *
 * @param onOpenReceived opens the receiver's new copy, by its typed route.
 */
@Composable
fun ItineraryShareScreen(
    itineraryId: String?,
    onBack: () -> Unit,
    onOpenReceived: (Any) -> Unit,
) {
    val context = LocalContext.current
    val cityPack = LocalCityPack.current
    val uid = LocalAuthService.current.currentUser?.uid
    val itineraries = LocalItineraryRepository.current
    val scope = rememberCoroutineScope()
    val share = rememberNearbyShare(enabled = true)

    val own by produceState<Itinerary?>(null, uid, itineraryId) {
        value = if (uid == null || itineraryId == null) null else try {
            itineraries.getItinerary(uid, itineraryId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    val names by produceState(emptyMap<String, String>()) { value = loadDistrictNames(context, cityPack) }
    var status by remember { mutableStateOf<ShareStatus?>(null) }
    var incoming by remember { mutableStateOf<Pair<SharedItinerary, String?>?>(null) }
    val payload = remember(own) { own?.toSharePayload() }

    val ownItem = own?.let { itinerary ->
        val title = itineraryTitle(itinerary.date, itinerary.districtIds.firstOrNull()?.let(names::get))
        val stops = pluralStringResource(R.plurals.stops_count, itinerary.activeStops.size, itinerary.activeStops.size)
        val colors = HackappTheme.appColors
        SandboxItem(
            key = OWN_KEY,
            color = colors.hero,
            shape = RoundedCornerShape(HackappCorners.Large),
            width = 220.dp,
            height = 140.dp,
            restitution = 0.3f,
        ) {
            ShareToken(title, stops)
        }
    }
    val items = listOfNotNull(ownItem.takeIf { payload != null })

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        HackappTopBar(
            stringResource(if (itineraryId == null) R.string.share_receive_title else R.string.share_title),
            onBack = onBack,
        )
        val peers = share?.peers?.size ?: 0
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val line = when {
                share == null -> stringResource(R.string.share_permissions_needed)
                itineraryId != null && own != null && payload == null -> stringResource(R.string.share_unavailable)
                peers == 0 -> stringResource(R.string.share_searching)
                itineraryId != null -> pluralStringResource(R.plurals.share_ready_to_send, peers, peers)
                else -> pluralStringResource(R.plurals.share_ready_to_receive, peers, peers)
            }
            Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            status?.let { Text(statusText(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PhysicsScreen(
                items = items,
                gravityLocked = true,
                sharing = SandboxSharing(
                    channel = share,
                    onThrow = { outcome ->
                        status = when (outcome) {
                            is ThrowOutcome.Sent -> ShareStatus.Sent(outcome.peerName?.let(::readableDeviceName))
                            is ThrowOutcome.NoPeers, is ThrowOutcome.SharingOff -> ShareStatus.NoPeers
                            is ThrowOutcome.NoTargetInDirection -> ShareStatus.NoTarget
                        }
                    },
                    onReceive = { _, _ -> },
                    payloadFor = { key -> payload.takeIf { key == OWN_KEY } },
                    onPayload = { data, from ->
                        val shared = decodeSharedItinerary(data)
                        if (shared == null) status = ShareStatus.ReceiveFailed else incoming = shared to from?.let(::readableDeviceName)
                    },
                ),
            )
            if (share == null) {
                HackappPrimaryButton(
                    text = stringResource(R.string.share_open_settings),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }

    incoming?.let { (shared, from) ->
        val itinerary = shared.itinerary
        AlertDialog(
            onDismissRequest = { incoming = null },
            title = {
                Text(
                    if (from != null) stringResource(R.string.share_received_from, from) else stringResource(R.string.share_received),
                )
            },
            text = { Text(itineraryTitle(itinerary.date, itinerary.districtIds.firstOrNull()?.let(names::get))) },
            confirmButton = {
                TextButton(onClick = {
                    incoming = null
                    val owner = uid ?: return@TextButton
                    scope.launch {
                        val copy = importSharedItinerary(shared, owner, cityPack.id, Instant.now())
                        if (copy == null) {
                            status = ShareStatus.ReceiveFailed
                            return@launch
                        }
                        try {
                            itineraries.saveItinerary(copy)
                            onOpenReceived(copy.openRoute())
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            status = ShareStatus.SaveFailed
                        }
                    }
                }) { Text(stringResource(R.string.share_add)) }
            },
            dismissButton = {
                TextButton(onClick = { incoming = null }) { Text(stringResource(R.string.share_decline)) }
            },
        )
    }
}

/** The thrown card: the Itinerary title and its number of Stops. */
@Composable
private fun ShareToken(title: String, stops: String) {
    val colors = HackappTheme.appColors
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Route, contentDescription = null, tint = colors.onHero, modifier = Modifier.size(28.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(stops, style = MaterialTheme.typography.labelMedium, color = colors.onHero)
    }
}

@Composable
private fun statusText(status: ShareStatus): String = when (status) {
    is ShareStatus.Sent -> status.to?.let { stringResource(R.string.share_sent_to, it) } ?: stringResource(R.string.share_sent)
    ShareStatus.NoPeers -> stringResource(R.string.share_no_peers)
    ShareStatus.NoTarget -> stringResource(R.string.share_no_target)
    ShareStatus.ReceiveFailed -> stringResource(R.string.share_receive_error)
    ShareStatus.SaveFailed -> stringResource(R.string.share_save_error)
}

/** Nearby names are "<model> #abcd"; the suffix only keeps names unique. */
internal fun readableDeviceName(name: String): String = name.substringBeforeLast(" #").ifBlank { name }
