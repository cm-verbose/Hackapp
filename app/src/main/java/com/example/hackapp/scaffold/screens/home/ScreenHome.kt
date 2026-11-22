package com.example.hackapp.scaffold.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.ui.components.AppBottomBar
import com.example.hackapp.ui.components.AppTab
import com.example.hackapp.ui.components.PlanItineraryCard
import kotlinx.serialization.Serializable

@Serializable
object ScreenHomeDestination

/** Home shell: the full page (Underway, suggestions, Itineraries in Planning) comes in step 2. */
@Composable
fun ScreenHome(onPlanItinerary: () -> Unit, onSelectTab: (AppTab) -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    val cityName = LocalCityPack.current.displayName.get(language).orEmpty()
    Scaffold(bottomBar = { AppBottomBar(selected = AppTab.Home, onSelect = onSelectTab) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlanItineraryCard(cityName = cityName, onPlan = onPlanItinerary, modifier = Modifier.fillMaxWidth())
        }
    }
}
