package com.example.hackapp.scaffold.screens.itinerary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.example.hackapp.scaffold.composables.mainbottombar.MainBottomBar
import com.example.hackapp.scaffold.navigation.PastItineraryRoute
import com.example.hackapp.scaffold.navigation.PlanningDateTimeRoute
import com.example.hackapp.scaffold.screens.onboarding.LoadState
import com.example.hackapp.scaffold.screens.share.FlickCard
import com.example.hackapp.scaffold.screens.share.ItineraryFlickOverlay
import com.example.hackapp.ui.components.ErrorState
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.components.PlanItineraryCard
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
object ScreenItineraryDestination

@Composable
fun ScreenItinerary(
    controller: NavHostController,
    outerPaddingValues: PaddingValues,
    onPlanItinerary: () -> Unit,
    onOpenItinerary: (Any) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val cityPack = LocalCityPack.current
    val auth = LocalAuthService.current
    val repository = LocalItineraryRepository.current
    val viewModel: ItinerariesViewModel = viewModel(
        // A bottom tab restores its saved ViewModel, which must not outlive the signed-in account.
        key = "itineraries-${auth.currentUser?.uid}",
        factory = ItinerariesViewModel.factory(auth.currentUser?.uid, repository) {
            loadDistrictNames(context, cityPack)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = appLocale().language
    // Screen bounds of the drafts, by id, while they are being shared, else null.
    var sharingFrom by remember { mutableStateOf<Map<String, Rect>?>(null) }
    Box(Modifier.fillMaxSize()) {
        Scaffold(bottomBar = { MainBottomBar(controller, outerPaddingValues) }) { paddingValues ->
            ItinerariesPage(
                state = state,
                cityName = cityPack.displayName.get(language).orEmpty(),
                onPlanItinerary = onPlanItinerary,
                onOpen = { onOpenItinerary(it.route) },
                onRequestDelete = viewModel::requestDelete,
                onConfirmDelete = viewModel::confirmDelete,
                onDismissDelete = viewModel::dismissDelete,
                onRetry = viewModel::retry,
                sharingPlanning = sharingFrom != null,
                onSharePlanning = { sharingFrom = it },
                modifier = Modifier.padding(paddingValues),
            )
        }
        val from = sharingFrom
        val planning = (state.content as? LoadState.Ready)?.value?.planning.orEmpty().filter { it.id in from.orEmpty() }
        if (from != null && planning.isNotEmpty()) {
            val scheme = MaterialTheme.colorScheme
            ItineraryFlickOverlay(
                cards = planning.map { card ->
                    FlickCard(card.id, from.getValue(card.id), RoundedCornerShape(HackappCorners.Medium), scheme.surfaceContainerLowest) {
                        ItineraryCard(card, onClick = {}, modifier = Modifier.fillMaxSize(), trailing = { DraftMenu {} })
                    }
                },
                onClose = { sharingFrom = null },
            )
        } else if (from != null) {
            // The drafts are gone (deleted or started): nothing left to share.
            LaunchedEffect(Unit) { sharingFrom = null }
        }
    }
}

/** Figma ItinerariesScreen (110:1395) and ItinerariesScreen_Empty (110:1593). */
@Composable
fun ItinerariesPage(
    state: ItinerariesUiState,
    cityName: String,
    onPlanItinerary: () -> Unit,
    onOpen: (ItineraryCardUi) -> Unit,
    onRequestDelete: (String) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** The drafts are being shared: their cards are hidden, the share overlay draws them. */
    sharingPlanning: Boolean = false,
    /** A long press on a draft, with every draft's bounds on screen by id. */
    onSharePlanning: (Map<String, Rect>) -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.nav_itineraries),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (state.deleteFailed) DeleteErrorNote()
        when (val content = state.content) {
            LoadState.Loading -> LoadingState()
            LoadState.Failed -> ErrorState(onRetry = onRetry)
            is LoadState.Ready -> if (content.value.isEmpty) {
                Text(
                    stringResource(R.string.itineraries_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                PlanItineraryCard(cityName = cityName, onPlan = onPlanItinerary, modifier = Modifier.fillMaxWidth())
            } else {
                ItinerarySectionList(content.value, onOpen, onRequestDelete, sharingPlanning, onSharePlanning)
            }
        }
    }
    if (state.pendingDeleteId != null) {
        DeleteItineraryDialog(onConfirm = onConfirmDelete, onDismiss = onDismissDelete)
    }
}

@Composable
private fun ItinerarySectionList(
    content: ItinerariesContent,
    onOpen: (ItineraryCardUi) -> Unit,
    onRequestDelete: (String) -> Unit,
    sharingPlanning: Boolean,
    onSharePlanning: (Map<String, Rect>) -> Unit,
) {
    if (content.planning.isNotEmpty()) {
        SectionLabel(stringResource(R.string.home_planning_section))
        val haptics = LocalHapticFeedback.current
        val bounds = remember { mutableMapOf<String, Rect>() }
        content.planning.forEach { card ->
            // Press and hold a draft to pile every draft up and flick one to a friend.
            ItineraryCard(
                card,
                onClick = { onOpen(card) },
                modifier = Modifier
                    .onGloballyPositioned { bounds[card.id] = it.boundsInRoot() }
                    .alpha(if (sharingPlanning) 0f else 1f),
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSharePlanning(content.planning.mapNotNull { c -> bounds[c.id]?.let { c.id to it } }.toMap())
                },
                onLongClickLabel = stringResource(R.string.share_title),
                trailing = { DraftMenu { onRequestDelete(card.id) } },
            )
        }
    }
    if (content.past.isNotEmpty()) {
        SectionLabel(stringResource(R.string.itineraries_past))
        content.past.forEach { card -> ItineraryCard(card, onClick = { onOpen(card) }) }
    }
    if (content.archived.isNotEmpty()) {
        SectionLabel(stringResource(R.string.itineraries_archived))
        content.archived.forEach { card -> ItineraryCard(card, onClick = { onOpen(card) }) }
    }
}

@Composable
private fun DraftMenu(onDelete: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.itinerary_menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

private val PreviewContent = ItinerariesContent(
    planning = listOf(
        ItineraryCardUi("1", LocalDate.of(2026, 11, 28), "Verdun", 3, null, PlanningDateTimeRoute("1")),
        ItineraryCardUi("2", LocalDate.of(2026, 12, 6), "Rosemont", 2, null, PlanningDateTimeRoute("2")),
    ),
    past = listOf(ItineraryCardUi("3", LocalDate.of(2026, 11, 14), "Le Plateau", 4, 3, PastItineraryRoute("3"))),
    archived = listOf(ItineraryCardUi("4", LocalDate.of(2026, 11, 20), "Verdun", 2, null, PastItineraryRoute("4"))),
)

@Composable
private fun ItinerariesPreviewPage(content: LoadState<ItinerariesContent>) = HackappTheme {
    ItinerariesPage(
        state = ItinerariesUiState(content = content),
        cityName = "Montréal",
        onPlanItinerary = {},
        onOpen = {},
        onRequestDelete = {},
        onConfirmDelete = {},
        onDismissDelete = {},
        onRetry = {},
    )
}

@Preview(name = "Itineraries", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ItinerariesPreview() = ItinerariesPreviewPage(LoadState.Ready(PreviewContent))

@Preview(name = "Itineraries · empty", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun ItinerariesEmptyPreview() =
    ItinerariesPreviewPage(LoadState.Ready(ItinerariesContent(emptyList(), emptyList(), emptyList())))

@Preview(name = "Itineraries · error", showBackground = true, widthDp = 412, heightDp = 400)
@Composable
private fun ItinerariesErrorPreview() = ItinerariesPreviewPage(LoadState.Failed)
