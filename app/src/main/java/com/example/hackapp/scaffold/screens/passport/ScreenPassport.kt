package com.example.hackapp.scaffold.screens.passport

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.hackapp.R
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.passport.LocalStampRepository
import com.example.hackapp.passport.PassportStamp
import com.example.hackapp.passport.PassportState
import com.example.hackapp.scaffold.composables.mainbottombar.MainBottomBar
import com.example.hackapp.scaffold.sandbox.PhysicsScreen
import com.example.hackapp.scaffold.sandbox.SandboxItem
import com.example.hackapp.scaffold.sandbox.sandboxCapacity
import com.example.hackapp.scaffold.screens.itinerary.loadDistrictNames
import com.example.hackapp.scaffold.screens.passport.gravityLock.SimulationGravityLockButton
import com.example.hackapp.ui.components.EmptyState
import com.example.hackapp.ui.components.ErrorState
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.components.Stamp
import com.example.hackapp.ui.components.StampDisc
import com.example.hackapp.ui.components.StampDiscSize
import com.example.hackapp.ui.components.stampInitials
import com.example.hackapp.ui.theme.HackappPalette
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Serializable
object ScreenPassportDestination

@Composable
fun ScreenPassport(controller: NavHostController, outerPaddingValues: PaddingValues) {
    val repository = LocalStampRepository.current
    val uid = LocalAuthService.current.currentUser?.uid
    var attempt by remember { mutableIntStateOf(0) }
    val passport = remember(repository, uid, attempt) {
        if (uid == null) flowOf(PassportState.Loaded(emptyList()))
        else repository.observePassport(uid).catch { emit(PassportState.Unavailable) }
    }
    val state by passport.collectAsStateWithLifecycle<PassportState?>(initialValue = null)
    var isGravityLocked by rememberSaveable { mutableStateOf(true) }
    val stamps = (state as? PassportState.Loaded)?.stamps.orEmpty()

    Scaffold(
        bottomBar = { MainBottomBar(controller, outerPaddingValues) },
        floatingActionButton = {
            if (stamps.isNotEmpty()) {
                SimulationGravityLockButton(
                    isLocked = isGravityLocked,
                    onLockedChange = { isGravityLocked = it },
                    lockedDescription = pendingString("passport_gravity_unlock"),
                    unlockedDescription = pendingString("passport_gravity_lock"),
                )
            }
        },
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues).fillMaxSize()) {
            when (state) {
                null -> LoadingState(Modifier.fillMaxSize())
                PassportState.Unavailable -> ErrorState(onRetry = { attempt++ }, modifier = Modifier.fillMaxSize())
                is PassportState.Loaded ->
                    if (stamps.isEmpty()) {
                        EmptyState(
                            modifier = Modifier.fillMaxSize(),
                            title = stringResource(R.string.end_of_day_no_stamp),
                            body = pendingString("passport_empty_body"),
                        )
                    } else {
                        StampSandbox(stamps, isGravityLocked)
                    }
            }
        }
    }
}

/** Stamps as physical objects. They stay on this device: nothing here is sent or saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StampSandbox(stamps: List<PassportStamp>, gravityLocked: Boolean) {
    val context = LocalContext.current
    val cityPack = LocalCityPack.current
    val districtNames by produceState(emptyMap<String, String>(), cityPack) {
        value = loadDistrictNames(context, cityPack)
    }
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val capacity = with(density) {
            sandboxCapacity(maxWidth.toPx(), maxHeight.toPx(), StampDiscSize.toPx(), this.density)
        }
        val shown = remember(stamps, capacity) { stamps.take(capacity) }
        val items = remember(shown, districtNames, dateFormat) {
            shown.map { stamp ->
                val description = listOf(
                    stamp.name,
                    districtNames[stamp.districtId] ?: stamp.districtId,
                    stamp.date.format(dateFormat),
                ).joinToString(", ")
                SandboxItem(
                    key = stamp.key,
                    color = HackappPalette.AccentLemon,
                    shape = CircleShape,
                    width = StampDiscSize,
                    height = StampDiscSize,
                ) {
                    StampDisc(
                        initials = stampInitials(stamp.name),
                        modifier = Modifier.fillMaxSize().clearAndSetSemantics {
                            contentDescription = description
                            role = Role.Button
                            onClick {
                                openKey = stamp.key
                                true
                            }
                        },
                    )
                }
            }
        }
        PhysicsScreen(
            items = items,
            gravityLocked = gravityLocked,
            onItemClick = { openKey = it.key as String },
        )
    }

    stamps.firstOrNull { it.key == openKey }?.let { stamp ->
        ModalBottomSheet(onDismissRequest = { openKey = null }) {
            Stamp(
                initials = stampInitials(stamp.name),
                stampName = stamp.name,
                date = stamp.date.format(dateFormat),
                district = districtNames[stamp.districtId] ?: stamp.districtId,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            )
        }
    }
}

/**
 * Text the Copy Editor is adding to strings.xml. Returns null until the key exists, so no text is
 * hardcoded. Replace each call with stringResource(R.string.key) once the keys are in.
 */
@SuppressLint("DiscouragedApi", "LocalContextResourcesRead")
@Composable
private fun pendingString(name: String): String? {
    val context = LocalContext.current
    val id = context.resources.getIdentifier(name, "string", context.packageName)
    return if (id != 0) stringResource(id) else null
}
