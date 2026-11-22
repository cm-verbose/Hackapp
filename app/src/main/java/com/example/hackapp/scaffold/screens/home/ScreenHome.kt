package com.example.hackapp.scaffold.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.hackapp.R
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.domain.FlowViewModel
import com.example.hackapp.scaffold.screens.home.bottombar.MainBottomBar
import com.example.hackapp.ui.components.ItineraryStrip
import com.example.hackapp.ui.components.PlanItineraryCard
import com.example.hackapp.ui.components.StripStop
import com.example.hackapp.ui.components.UnderwayItineraryCard
import com.example.hackapp.ui.motion.entrance
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

@Serializable
object ScreenHomeDestination

/** Home: greeting, the Itinerary in progress (if any) and the call to plan a new one. */
@Composable
fun ScreenHome(
    controller: NavHostController,
    outerPaddingValues: PaddingValues,
    vm: FlowViewModel,
    onPlanItinerary: () -> Unit,
    onResume: () -> Unit,
    onSettings: () -> Unit,
) {
    val language = LocalConfiguration.current.locales[0].language
    val cityName = LocalCityPack.current.displayName.get(language).orEmpty()
    val profile by vm.profile.collectAsState()
    val underway by vm.underway.collectAsState()
    val hhmm = DateTimeFormatter.ofPattern("HH:mm")

    Scaffold(bottomBar = {
        MainBottomBar(controller, outerPaddingValues)
    }) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_greeting, profile?.name?.substringBefore(' ').orEmpty()).trim(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                }
            }
            underway?.takeIf { !it.finished }?.let { p ->
                val next = p.current
                UnderwayItineraryCard(
                    itineraryName = p.itinerary.date,
                    nextStop = next?.let { stringResource(R.string.home_next_stop, it.title, it.start.format(hhmm)) }.orEmpty(),
                    onResume = onResume,
                    itineraryStrip = {
                        ItineraryStrip(p.itinerary.stops.map { StripStop(it.start.format(hhmm), it.type) }, currentIndex = p.index)
                    },
                    modifier = Modifier.fillMaxWidth().entrance(0),
                )
            }
            PlanItineraryCard(cityName = cityName, onPlan = onPlanItinerary, modifier = Modifier.fillMaxWidth().entrance(1))
        }
    }
}
