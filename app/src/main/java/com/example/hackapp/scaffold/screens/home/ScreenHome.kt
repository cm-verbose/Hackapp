package com.example.hackapp.scaffold.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.hackapp.R
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.itinerary.LocalItineraryRepository
import com.example.hackapp.profile.LocalProfileRepository
import com.example.hackapp.scaffold.composables.mainbottombar.MainBottomBar
import com.example.hackapp.scaffold.navigation.PlanningDistrictPickRoute
import com.example.hackapp.scaffold.navigation.UnderwayLegRoute
import com.example.hackapp.scaffold.screens.itinerary.ItineraryCard
import com.example.hackapp.scaffold.screens.itinerary.ItineraryCardUi
import com.example.hackapp.scaffold.screens.itinerary.SectionLabel
import com.example.hackapp.scaffold.screens.itinerary.itineraryTitle
import com.example.hackapp.scaffold.screens.itinerary.loadDistrictNames
import com.example.hackapp.scaffold.screens.onboarding.LoadState
import com.example.hackapp.services.LocalCityServices
import com.example.hackapp.ui.components.DataAttribution
import com.example.hackapp.ui.components.ErrorState
import com.example.hackapp.ui.components.HackappIcon
import com.example.hackapp.ui.components.ItineraryStrip
import com.example.hackapp.ui.components.ItinerarySuggestion
import com.example.hackapp.ui.components.ItinerarySuggestionsCard
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.components.PlanItineraryCard
import com.example.hackapp.ui.components.StripStop
import com.example.hackapp.ui.components.UnderwayItineraryCard
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.ui.theme.StopType
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
object ScreenHomeDestination

@Composable
fun ScreenHome(
    controller: NavHostController,
    outerPaddingValues: PaddingValues,
    onPlanItinerary: () -> Unit,
    onOpenItinerary: (Any) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val cityPack = LocalCityPack.current
    val auth = LocalAuthService.current
    val itineraries = LocalItineraryRepository.current
    val profiles = LocalProfileRepository.current
    val events = LocalCityServices.current.events
    val viewModel: HomeViewModel = viewModel(
        key = "home-${auth.currentUser?.uid}",
        factory = HomeViewModel.factory(auth.currentUser?.uid, itineraries, profiles, events, cityPack.timeZone) {
            loadDistrictNames(context, cityPack)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = LocalConfiguration.current.locales[0].language
    Scaffold(bottomBar = { MainBottomBar(controller, outerPaddingValues) }) { paddingValues ->
        HomePage(
            state = state,
            cityName = cityPack.displayName.get(language).orEmpty(),
            onPlanItinerary = onPlanItinerary,
            onOpenItinerary = onOpenItinerary,
            onOpenSettings = onOpenSettings,
            onRetry = viewModel::retry,
            modifier = Modifier.padding(paddingValues),
        )
    }
}

/** Figma HomeScreen (29:348). The Plan card stays available while the lists load or fail. */
@Composable
fun HomePage(
    state: HomeUiState,
    cityName: String,
    onPlanItinerary: () -> Unit,
    onOpenItinerary: (Any) -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = state.itineraries
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.firstName?.let { stringResource(R.string.home_greeting, it) }
                    ?: stringResource(R.string.home_greeting_anonymous),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        }
        (content as? LoadState.Ready)?.value?.underway?.let { underway ->
            UnderwayItineraryCard(
                itineraryName = itineraryTitle(underway.date, underway.districtName),
                nextStop = if (underway.nextStopName != null) {
                    stringResource(R.string.home_next_stop, underway.nextStopName)
                } else {
                    ""
                },
                onResume = { onOpenItinerary(underway.route) },
                itineraryStrip = { ItineraryStrip(underway.strip, currentIndex = underway.currentIndex) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PlanItineraryCard(cityName = cityName, onPlan = onPlanItinerary, modifier = Modifier.fillMaxWidth())
        if (state.suggestions.isNotEmpty()) {
            ItinerarySuggestionsCard(
                suggestions = state.suggestions.map { ItinerarySuggestion(it.icon, it.title) },
                modifier = Modifier.fillMaxWidth(),
            )
            DataAttribution()
        }
        when (content) {
            LoadState.Loading -> LoadingState()
            LoadState.Failed -> ErrorState(onRetry = onRetry)
            is LoadState.Ready -> if (content.value.planning.isNotEmpty()) {
                SectionLabel(stringResource(R.string.home_planning_section))
                content.value.planning.forEach { card ->
                    ItineraryCard(card, onClick = { onOpenItinerary(card.route) })
                }
            }
        }
    }
}

private val PreviewHomeState = HomeUiState(
    firstName = "Nikola",
    suggestions = listOf(
        EventSuggestion("1", "Soirée jazz sur le Plateau", HackappIcon.MusicNote),
        EventSuggestion("2", "Spectacle d’humour", HackappIcon.SentimentSatisfied),
    ),
    itineraries = LoadState.Ready(
        HomeItineraries(
            underway = UnderwayUi(
                date = LocalDate.of(2026, 11, 28),
                districtName = "Le Plateau",
                nextStopTime = "19:30",
                nextStopName = "Bistro du coin",
                strip = listOf(StripStop("18:30", StopType.Music), StripStop("19:30", StopType.Meal)),
                currentIndex = 1,
                route = UnderwayLegRoute,
            ),
            planning = listOf(
                ItineraryCardUi("1", LocalDate.of(2026, 11, 28), "Verdun", 3, null, PlanningDistrictPickRoute("1")),
            ),
        ),
    ),
)

@Composable
private fun HomePreviewPage(state: HomeUiState) = HackappTheme {
    HomePage(
        state = state,
        cityName = "Montréal",
        onPlanItinerary = {},
        onOpenItinerary = {},
        onOpenSettings = {},
        onRetry = {},
    )
}

@Preview(name = "Home", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun HomePreview() = HomePreviewPage(PreviewHomeState)

@Preview(name = "Home · empty", showBackground = true, widthDp = 412, heightDp = 500)
@Composable
private fun HomeEmptyPreview() =
    HomePreviewPage(HomeUiState(itineraries = LoadState.Ready(HomeItineraries(null, emptyList()))))

@Preview(name = "Home · error", showBackground = true, widthDp = 412, heightDp = 600)
@Composable
private fun HomeErrorPreview() = HomePreviewPage(HomeUiState(itineraries = LoadState.Failed))
