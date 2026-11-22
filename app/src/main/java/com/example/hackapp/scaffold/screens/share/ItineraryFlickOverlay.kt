package com.example.hackapp.scaffold.screens.share

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.itinerary.LocalItineraryRepository
import com.example.hackapp.itinerary.SharedItinerary
import com.example.hackapp.itinerary.decodeSharedItinerary
import com.example.hackapp.itinerary.toSharePayload
import com.example.hackapp.scaffold.sandbox.PhysicsScreen
import com.example.hackapp.scaffold.sandbox.SandboxItem
import com.example.hackapp.scaffold.sandbox.SandboxSharing
import com.example.hackapp.scaffold.sandbox.ThrowOutcome
import com.example.hackapp.scaffold.sandbox.share.ShareOffer
import com.example.hackapp.scaffold.sandbox.share.rememberNearbyShare
import com.example.hackapp.scaffold.screens.itinerary.loadDistrictNames
import com.example.hackapp.ui.components.HackappPrimaryButton
import kotlinx.coroutines.CancellationException

/** How long the rest of the screen takes to fade to the overlay colour. */
private const val FADE_MILLIS = 400

/**
 * An Itinerary card as it sits on screen when a long press starts sharing, so it can fall from
 * that exact spot. [content] draws the card itself, at the size of [boundsInRoot].
 */
class FlickCard(
    val itineraryId: String,
    val boundsInRoot: Rect,
    val shape: Shape,
    val color: Color,
    val content: @Composable BoxScope.() -> Unit,
)

/**
 * Share mode, opened by a long press on Itinerary cards (Home's active Itinerary, or the
 * Itineraries screen's drafts). The screen fades to the "Day or night out" card colour
 * (primaryContainer) and the [cards] drop from where they were, pulled by the phone's tilt.
 * Swipe a card to move it; flick it toward a nearby phone to send a copy, as on the Share
 * Itinerary page. Itineraries thrown from a nearby phone can be received here too.
 *
 * Place it over the whole screen. Hide the original cards while it is shown.
 */
@Composable
fun ItineraryFlickOverlay(
    cards: List<FlickCard>,
    onClose: () -> Unit,
    onOpenReceived: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val cityPack = LocalCityPack.current
    val density = LocalDensity.current
    val uid = LocalAuthService.current.currentUser?.uid
    val itineraries = LocalItineraryRepository.current
    val share = rememberNearbyShare(enabled = true, offer = ShareOffer.Itinerary)
    val scheme = MaterialTheme.colorScheme

    val ids = remember(cards) { cards.map { it.itineraryId } }
    val payloads by produceState(emptyMap<String, String?>(), uid, ids) {
        value = if (uid == null) emptyMap() else ids.associateWith { id ->
            try {
                itineraries.getItinerary(uid, id)?.toSharePayload()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }
    val names by produceState(emptyMap<String, String>()) { value = loadDistrictNames(context, cityPack) }
    var status by remember { mutableStateOf<ShareStatus?>(null) }
    var incoming by remember { mutableStateOf<Pair<SharedItinerary, String?>?>(null) }

    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(FADE_MILLIS)) }
    // Where this overlay sits on screen, to turn the cards' screen bounds into world positions.
    var origin by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier
            .fillMaxSize()
            .drawBehind { drawRect(scheme.primaryContainer.copy(alpha = fade.value)) }
            // The screen underneath must not react while sharing.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        // The status gets its own band, so cards never cover it; the world fills the rest.
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                    .graphicsLayer { alpha = fade.value },
                verticalAlignment = Alignment.Top,
            ) {
                val peers = share?.peers?.size ?: 0
                val line = status?.let { statusText(it) } ?: when {
                    share == null -> stringResource(R.string.share_permissions_needed)
                    peers == 0 -> stringResource(R.string.share_searching)
                    else -> pluralStringResource(R.plurals.share_ready_to_send, peers, peers)
                }
                // Always two lines high: a taller band would resize the world and reset the cards.
                Text(
                    line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onPrimaryContainer,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = scheme.onPrimaryContainer)
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        if (origin == null) {
                            size = it.size
                            origin = it.positionInRoot()
                        }
                    },
            ) {
                origin?.let { topLeft ->
                    val items = remember(cards, topLeft, size) {
                        val screen = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                        cards.map { card ->
                            val bounds = card.boundsInRoot.translate(-topLeft)
                            with(density) {
                                SandboxItem(
                                    key = card.itineraryId,
                                    color = card.color,
                                    shape = card.shape,
                                    width = bounds.width.toDp(),
                                    height = bounds.height.toDp(),
                                    restitution = 0.3f,
                                    // A card out of the world's view (scrolled away, or under the status) starts at a free spot.
                                    startCenterPx = bounds.center.takeIf { screen.contains(it) },
                                    content = card.content,
                                )
                            }
                        }
                    }
                    PhysicsScreen(
                        items = items,
                        gravityLocked = false,
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
                            payloadFor = { key -> payloads[key] },
                            onPayload = { data, from ->
                                val shared = decodeSharedItinerary(data)
                                if (shared == null) status = ShareStatus.ReceiveFailed else incoming = shared to from?.let(::readableDeviceName)
                            },
                        ),
                    )
                }
            }
        }

        if (share == null) {
            HackappPrimaryButton(
                text = stringResource(R.string.share_open_settings),
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
    }

    incoming?.let { (shared, from) ->
        IncomingItineraryDialog(
            shared = shared,
            fromName = from,
            districtNames = names,
            onDismiss = { incoming = null },
            onStatus = { status = it },
            onOpenReceived = onOpenReceived,
        )
    }
}
