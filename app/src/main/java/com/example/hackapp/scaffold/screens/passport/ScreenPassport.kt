package com.example.hackapp.scaffold.screens.passport

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.hackapp.passport.LocalKeepsakeStore
import com.example.hackapp.passport.LocalStampPhotoStore
import com.example.hackapp.passport.StampKeepsake
import com.example.hackapp.scaffold.navigation.ShareStampRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.hackapp.R
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.passport.LocalStampRepository
import com.example.hackapp.passport.PassportState
import com.example.hackapp.scaffold.composables.mainbottombar.MainBottomBar
import com.example.hackapp.scaffold.screens.itinerary.appLocale
import com.example.hackapp.scaffold.screens.itinerary.loadDistrictNames
import com.example.hackapp.ui.components.ChoiceOption
import com.example.hackapp.ui.components.HackappTopBar
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Serializable
object ScreenPassportDestination

@Composable
fun ScreenPassport(controller: NavHostController, outerPaddingValues: PaddingValues) {
    val repository = LocalStampRepository.current
    val uid = LocalAuthService.current.currentUser?.uid
    val cityPack = LocalCityPack.current
    val context = LocalContext.current.applicationContext
    var attempt by remember { mutableIntStateOf(0) }
    val passport = remember(repository, uid, attempt) {
        if (uid == null) flowOf(PassportState.Loaded(emptyList()))
        else repository.observePassport(uid).catch { emit(PassportState.Unavailable) }
    }
    val state by passport.collectAsStateWithLifecycle<PassportState?>(initialValue = null)
    val districtNames by produceState(emptyMap<String, String>(), cityPack) {
        value = loadDistrictNames(context, cityPack)
    }
    val locale = appLocale()
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val cityName = cityPack.displayName.get(locale.language).orEmpty()
    // One option per City Pack in this build. Another pack adds its own segment here.
    val cities = remember(cityPack.id, cityName) { listOf(ChoiceOption(cityPack.id, cityName)) }
    var selectedCity by rememberSaveable(cityPack.id) { mutableStateOf(cityPack.id) }
    var gravityLocked by rememberSaveable { mutableStateOf(true) }
    val photoStore = LocalStampPhotoStore.current
    val keepsakeStore = LocalKeepsakeStore.current
    val photoVersion by (photoStore?.version ?: remember { MutableStateFlow(0) }).collectAsStateWithLifecycle()
    val keepsakeFlow by produceState<StateFlow<Map<String, List<StampKeepsake>>>?>(null, keepsakeStore, uid) {
        value = if (uid == null) null else keepsakeStore?.observe(uid)
    }
    val keepsakeMap by (keepsakeFlow ?: remember { MutableStateFlow(emptyMap()) }).collectAsStateWithLifecycle()
    val received = keepsakeMap[uid].orEmpty()
    val stampKeys = (state as? PassportState.Loaded)?.stamps?.map { it.key }.orEmpty()
    val photos by produceState(emptyMap<String, ImageBitmap>(), stampKeys, photoVersion, uid) {
        val owner = uid ?: return@produceState
        val store = photoStore ?: return@produceState
        value = stampKeys.mapNotNull { key -> store.load(owner, key)?.let { key to it.asImageBitmap() } }.toMap()
    }
    val keepsakePhotos by produceState(emptyMap<String, ImageBitmap>(), received) {
        value = withContext(Dispatchers.Default) {
            received.mapNotNull { k ->
                k.photo?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
                    ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    ?.let { k.id to it.asImageBitmap() }
            }.toMap()
        }
    }
    val content = remember(state, selectedCity, districtNames, dateFormat, photos) {
        when (val base = passportContent(state, selectedCity, districtNames, dateFormat)) {
            is PassportContent.Ready -> base.copy(stamps = base.stamps.map { it.copy(photo = photos[it.key]) })
            else -> base
        }
    }
    val keepsakes = remember(received, districtNames, dateFormat, keepsakePhotos, selectedCity) {
        received.filter { it.cityId == selectedCity }.map { it.toUi(districtNames, dateFormat, keepsakePhotos[it.id]) }
    }

    val scope = rememberCoroutineScope()
    var photoFor by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val key = photoFor
        if (uri != null && key != null && uid != null) scope.launch { photoStore?.setFromUri(uid, key, uri) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        val key = photoFor
        if (bitmap != null && key != null && uid != null) scope.launch { photoStore?.setFromBitmap(uid, key, bitmap) }
    }
    val actions = StampActions(
        onPickPhoto = { key ->
            photoFor = key
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onTakePhoto = { key ->
            photoFor = key
            camera.launch(null)
        },
        onRemovePhoto = { key -> if (uid != null) scope.launch { photoStore?.remove(uid, key) } },
        onShare = { key -> controller.navigate(ShareStampRoute(key)) },
        onReceive = { controller.navigate(ShareStampRoute(null)) },
        onRemoveKeepsake = { key ->
            if (uid != null) scope.launch { keepsakeStore?.remove(uid, key.removePrefix("keepsake:")) }
        },
    )

    Scaffold(
        topBar = {
            HackappTopBar(
                title = stringResource(R.string.passport_title),
                onBack = { controller.navigateUp() },
            )
        },
        bottomBar = { MainBottomBar(controller, outerPaddingValues) },
    ) { paddingValues ->
        PassportPage(
            content = content,
            cities = cities,
            selectedCity = selectedCity,
            gravityLocked = gravityLocked,
            onCityChange = { selectedCity = it },
            onGravityLockedChange = { gravityLocked = it },
            onRetry = { attempt++ },
            modifier = Modifier.padding(paddingValues),
            keepsakes = keepsakes,
            actions = actions,
        )
    }
}
