package com.example.hackapp.scaffold.screens.passport

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
    val content = remember(state, selectedCity, districtNames, dateFormat) {
        passportContent(state, selectedCity, districtNames, dateFormat)
    }

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
        )
    }
}
