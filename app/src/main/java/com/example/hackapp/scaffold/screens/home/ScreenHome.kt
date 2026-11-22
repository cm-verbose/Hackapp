package com.example.hackapp.scaffold.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.scaffold.composables.mainbottombar.MainBottomBar
import com.example.hackapp.scaffold.screens.planning.GoogleLogoffButton
import com.example.hackapp.ui.components.PlanItineraryCard
import kotlinx.serialization.Serializable

@Serializable
object ScreenHomeDestination

/** Home shell: the full page (Underway, suggestions, Itineraries in Planning) comes in step 2. */
@Composable
fun ScreenHome(
    controller: NavHostController,
    outerPaddingValues: PaddingValues,
    onPlanItinerary: () -> Unit,
    onLogOff: () -> Unit,
) {
    val language = LocalConfiguration.current.locales[0].language
    val cityName = LocalCityPack.current.displayName.get(language).orEmpty()
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
            PlanItineraryCard(cityName = cityName, onPlan = onPlanItinerary, modifier = Modifier.fillMaxWidth())
            GoogleLogoffButton(onLogOff = onLogOff, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
