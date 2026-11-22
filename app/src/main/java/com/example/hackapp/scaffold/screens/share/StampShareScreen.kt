package com.example.hackapp.scaffold.screens.share

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.passport.LocalKeepsakeStore
import com.example.hackapp.passport.LocalStampPhotoStore
import com.example.hackapp.passport.LocalStampRepository
import com.example.hackapp.passport.PassportStamp
import com.example.hackapp.passport.SharedStamp
import com.example.hackapp.passport.StampKeepsake
import com.example.hackapp.passport.decodeSharedStamp
import com.example.hackapp.passport.toSharedPayload
import com.example.hackapp.scaffold.sandbox.PhysicsScreen
import com.example.hackapp.scaffold.sandbox.SandboxItem
import com.example.hackapp.scaffold.sandbox.SandboxSharing
import com.example.hackapp.scaffold.sandbox.ThrowOutcome
import com.example.hackapp.scaffold.sandbox.share.ShareOffer
import com.example.hackapp.scaffold.sandbox.share.rememberNearbyShare
import com.example.hackapp.scaffold.screens.passport.stampStopType
import com.example.hackapp.ui.components.HackappTopBar
import com.example.hackapp.ui.components.StampDisc
import com.example.hackapp.ui.components.stampInitials
import com.example.hackapp.ui.theme.HackappTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

private const val STAMP_KEY = "own-stamp"

/**
 * Flick a Stamp, with its photo, to a phone nearby. On the friend's phone it flies in as it was
 * thrown; tapping it opens it, and Keep adds it as a keepsake: shown apart in their Passport and
 * never counted as one of their Stamps. With [stampKey] null the page only receives.
 */
@Composable
fun StampShareScreen(stampKey: String?, onBack: () -> Unit) {
    val uid = LocalAuthService.current.currentUser?.uid
    val stamps = LocalStampRepository.current
    val photos = LocalStampPhotoStore.current
    val keepsakes = LocalKeepsakeStore.current
    val cityPack = LocalCityPack.current
    val scope = rememberCoroutineScope()
    val share = rememberNearbyShare(enabled = true, offer = ShareOffer.Stamp.takeIf { stampKey != null })

    val own by produceState<Pair<PassportStamp, String?>?>(null, uid, stampKey) {
        value = if (uid == null || stampKey == null) null else try {
            val stamp = stamps.observeStamps(uid).first().firstOrNull { it.key == stampKey }
            stamp?.let { it to photos?.sharedCopy(uid, it.key) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    val ownPhoto = remember(own) { own?.second?.let(::decodePhoto) }
    val payload = remember(own) { own?.let { (stamp, photo) -> stamp.toSharedPayload(photo) } }
    var status by remember { mutableStateOf<String?>(null) }
    val catches = remember { Catches<SharedStamp>() }
    var opened by remember { mutableStateOf<Any?>(null) }
    val sentText = stringResource(R.string.share_sent)
    val noPeersText = stringResource(R.string.share_no_peers)
    val noTargetText = stringResource(R.string.share_no_target)
    val receiveErrorText = stringResource(R.string.share_receive_error)
    val savedText = stringResource(R.string.share_stamp_saved)

    val colors = HackappTheme.stopTypeColors
    val items = own?.let { (stamp, _) ->
        val disc = colors[stampStopType(stamp.kind, stamp.category)]
        listOf(
            SandboxItem(key = STAMP_KEY, color = disc, shape = CircleShape, width = 160.dp, height = 160.dp, restitution = 0.4f) {
                StampDisc(
                    initials = stampInitials(stamp.name),
                    discColor = disc,
                    inkColor = colors.onStopType,
                    photo = ownPhoto,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    }.orEmpty()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        HackappTopBar(
            stringResource(if (stampKey == null) R.string.share_stamp_receive_title else R.string.share_stamp_title),
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
                peers == 0 -> stringResource(R.string.share_searching)
                stampKey != null -> pluralStringResource(R.plurals.share_ready_to_send, peers, peers)
                else -> pluralStringResource(R.plurals.share_ready_to_receive, peers, peers)
            }
            Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PhysicsScreen(
                items = items,
                gravityLocked = true,
                sharing = SandboxSharing(
                    channel = share,
                    onThrow = { outcome ->
                        status = when (outcome) {
                            is ThrowOutcome.Sent -> sentText
                            is ThrowOutcome.NoPeers, is ThrowOutcome.SharingOff -> noPeersText
                            is ThrowOutcome.NoTargetInDirection -> noTargetText
                        }
                    },
                    onReceive = { _, _ -> },
                    payloadFor = { key -> payload.takeIf { key == STAMP_KEY } },
                    // A thrown Stamp flies in as it was thrown; tapping it opens it.
                    receivedItem = { data, from ->
                        val shared = decodeSharedStamp(data)
                        if (shared == null) {
                            status = receiveErrorText
                            null
                        } else {
                            val key = catches.add(shared, from?.let(::readableDeviceName))
                            val disc = colors[stampStopType(shared.kind, shared.category)]
                            val photo = shared.photo?.let(::decodePhoto)
                            SandboxItem(key = key, color = disc, shape = CircleShape, width = 160.dp, height = 160.dp, restitution = 0.4f) {
                                StampDisc(
                                    initials = stampInitials(shared.name),
                                    discColor = disc,
                                    inkColor = colors.onStopType,
                                    photo = photo,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    },
                    removedKeys = catches.removed,
                ),
                onItemClick = { item -> if (item.key in catches.byKey) opened = item.key },
            )
        }
    }

    val openedKey = opened
    openedKey?.let { catches.byKey[it] }?.let { (shared, from) ->
        val photo = remember(shared) { shared.photo?.let(::decodePhoto) }
        val disc = colors[stampStopType(shared.kind, shared.category)]
        AlertDialog(
            onDismissRequest = { opened = null },
            icon = {
                StampDisc(
                    initials = stampInitials(shared.name),
                    discColor = disc,
                    inkColor = colors.onStopType,
                    photo = photo,
                    modifier = Modifier.padding(4.dp).fillMaxWidth(0.4f).then(Modifier),
                )
            },
            title = {
                Text(
                    if (from != null) stringResource(R.string.share_stamp_received_from, shared.name, from)
                    else stringResource(R.string.share_stamp_received, shared.name),
                )
            },
            text = { Text(stringResource(R.string.share_stamp_keepsake_note)) },
            confirmButton = {
                TextButton(onClick = {
                    opened = null
                    val owner = uid ?: return@TextButton
                    if (shared.cityId != cityPack.id) {
                        status = receiveErrorText
                        return@TextButton
                    }
                    scope.launch {
                        keepsakes?.add(
                            owner,
                            StampKeepsake(
                                id = UUID.randomUUID().toString(),
                                name = shared.name,
                                kind = shared.kind,
                                category = shared.category,
                                cityId = shared.cityId,
                                districtId = shared.districtId,
                                date = shared.date,
                                shapeSeed = shared.shapeSeed,
                                fromName = from,
                                receivedAtMillis = System.currentTimeMillis(),
                                photo = shared.photo,
                            ),
                        )
                        catches.saved(openedKey)
                        status = savedText
                    }
                }) { Text(stringResource(R.string.share_stamp_keep)) }
            },
            dismissButton = {
                TextButton(onClick = { opened = null }) { Text(stringResource(R.string.share_decline)) }
            },
        )
    }
}

private fun decodePhoto(base64: String): ImageBitmap? =
    runCatching { Base64.decode(base64, Base64.NO_WRAP) }.getOrNull()
        ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        ?.asImageBitmap()
