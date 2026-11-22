package com.example.hackapp.scaffold.screens.planning

import com.example.hackapp.scaffold.screens.underway.currentUnderwayClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackapp.R
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.LabelKey
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.citypack.loadDistrictMap
import com.example.hackapp.itinerary.LocalItineraryRepository
import com.example.hackapp.profile.LocalProfileRepository
import com.example.hackapp.services.LocalCityServices
import com.example.hackapp.ui.components.BottomActions
import com.example.hackapp.ui.components.DataAttribution
import com.example.hackapp.ui.components.ErrorState
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.HackappSmallTextButton
import com.example.hackapp.ui.components.HackappSmallTonalButton
import com.example.hackapp.ui.components.HackappTopBar
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.components.RadioOptionRow
import com.example.hackapp.ui.components.StepProgress
import com.example.hackapp.ui.components.map.DistrictMarker
import com.example.hackapp.ui.components.map.DistrictPickMap
import com.example.hackapp.ui.components.map.MapViewportState
import com.example.hackapp.ui.components.map.PreviewDistrictMap
import com.example.hackapp.ui.components.map.rememberMapViewportState
import com.example.hackapp.ui.components.stopTypeLabel
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.ui.theme.StopType

/** Connects the District pick page to its ViewModel and to navigation. */
@Composable
fun DistrictPickScreen(
    itineraryId: String,
    onBack: () -> Unit,
    onContinue: (String) -> Unit,
) {
    val services = LocalCityServices.current
    val clock = currentUnderwayClock()
    val context = LocalContext.current
    val viewModel: DistrictPickViewModel = viewModel(
        factory = DistrictPickViewModel.factory(
            itineraryId = itineraryId,
            auth = LocalAuthService.current,
            itineraries = LocalItineraryRepository.current,
            events = services.events,
            cityPack = services.cityPack,
            loadMap = { loadDistrictMap(context.applicationContext, services.cityPack) },
            profiles = LocalProfileRepository.current,
            reconciler = services.stopFitReconciler(),
            planningNowMillis = { clock.planningNow().toEpochMilli() },
        ),
    )
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshSelection() }
    LaunchedEffect(state.continueToId) {
        state.continueToId?.let { id ->
            viewModel.onNavigated()
            onContinue(id)
        }
    }
    StopChangeWarningDialog(state.changeWarning, viewModel::confirmChange, viewModel::cancelChange)
    DistrictPickPage(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onDistrictTap = viewModel::onDistrictTap,
        onDismissSheet = viewModel::dismissSheet,
        onAdd = viewModel::addPreviewed,
        onRemove = viewModel::removePreviewed,
        onChooseReplaceTarget = viewModel::chooseReplaceTarget,
        onConfirmReplace = viewModel::confirmReplace,
        onContinue = viewModel::continueToMeals,
        onMoveSelected = viewModel::moveSelected,
    )
}

@Composable
fun DistrictPickPage(
    state: DistrictPickUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDistrictTap: (String) -> Unit,
    onDismissSheet: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onChooseReplaceTarget: (String) -> Unit,
    onConfirmReplace: () -> Unit,
    onContinue: () -> Unit,
    viewportState: MapViewportState = rememberMapViewportState(),
    onMoveSelected: (Int, Int) -> Unit = { _, _ -> },
) {
    val scheme = MaterialTheme.colorScheme
    // Until the list is full, adding is the main action and Continue waits in the app bar.
    val full = state.selected.size >= MAX_DISTRICTS
    Column(Modifier.fillMaxSize().background(scheme.surface)) {
        HackappTopBar(
            stringResource(R.string.district_pick_title),
            onBack = onBack,
            actions = {
                if (!full && !state.loading && !state.loadFailed) {
                    HackappSmallTextButton(
                        stringResource(R.string.action_continue),
                        onContinue,
                        enabled = state.canContinue,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            },
        )
        StepProgress(2, PLANNING_STEPS)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val map = state.map
            when {
                state.loading -> LoadingState(Modifier.align(Alignment.Center))
                state.loadFailed || map == null -> ErrorState(onRetry = onRetry, modifier = Modifier.align(Alignment.Center))
                else -> {
                    val markers = remember(map, state.eventsByDistrict, state.selected, state.previewId) {
                        map.districts.associate { d ->
                            val count = state.eventsByDistrict[d.id]?.count ?: 0
                            val order = state.selected.indexOf(d.id).takeIf { it >= 0 }?.plus(1)
                            d.id to DistrictMarker(
                                count = count,
                                pickable = isDistrictPickable(d.groupId, count, order != null),
                                showLabel = order != null || d.groupId != LINKED_CITY_GROUP,
                                order = order,
                                previewed = d.id == state.previewId,
                            )
                        }
                    }
                    DistrictPickMap(
                        map = map,
                        markers = markers,
                        viewportState = viewportState,
                        onDistrictTap = onDistrictTap,
                        onEmptyTap = onDismissSheet,
                    )
                    MapHeader(
                        state = state,
                        viewportState = viewportState,
                        onChipClick = onDistrictTap,
                        onMove = onMoveSelected,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { viewportState.setBottomInset(it.height.toFloat()) },
                    ) {
                        AnimatedVisibility(
                            visible = state.previewId != null,
                            enter = expandVertically(expandFrom = Alignment.Top),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top),
                        ) {
                            DistrictSheet(
                                state = state,
                                onDismiss = onDismissSheet,
                                onAdd = onAdd,
                                onRemove = onRemove,
                                onChooseReplaceTarget = onChooseReplaceTarget,
                                onConfirmReplace = onConfirmReplace,
                            )
                        }
                        // While the sheet is open this strip is part of it, so taps must not reach the map.
                        val actionsBackground = if (state.previewId != null) {
                            Modifier.background(scheme.surfaceContainerLow).pointerInput(Unit) {}
                        } else {
                            Modifier
                        }
                        BottomActions(actionsBackground) {
                            DataAttribution()
                            if (state.saveFailed) {
                                Text(
                                    stringResource(R.string.planning_save_error),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.error,
                                )
                            }
                            if (full) {
                                HackappPrimaryButton(
                                    text = stringResource(R.string.action_continue),
                                    onClick = onContinue,
                                    enabled = state.canContinue,
                                )
                            } else {
                                val previewed = state.previewId?.takeIf { it !in state.selected }
                                val position = state.selected.size + 1
                                HackappPrimaryButton(
                                    text = when {
                                        previewed == null -> stringResource(R.string.district_add_to_list)
                                        position == 1 -> stringResource(R.string.district_add_first)
                                        else -> stringResource(R.string.district_add_as_position, position)
                                    },
                                    onClick = onAdd,
                                    enabled = previewed != null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapHeader(
    state: DistrictPickUiState,
    viewportState: MapViewportState,
    onChipClick: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth()) {
        // A Surface, so touches on the header never select a District hidden behind it.
        Surface(
            color = scheme.surface.copy(alpha = 0.92f),
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { viewportState.setTopInset(it.height.toFloat()) },
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.district_pick_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                if (state.selected.isNotEmpty()) {
                    ReorderableDistrictList(state.selected, state::nameOf, onChipClick, onMove)
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, end = 16.dp), contentAlignment = Alignment.TopEnd) {
            FilledIconButton(
                onClick = { viewportState.reset(scope) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                ),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.ZoomOutMap, contentDescription = stringResource(R.string.district_map_reset))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DistrictSheet(
    state: DistrictPickUiState,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onChooseReplaceTarget: (String) -> Unit,
    onConfirmReplace: () -> Unit,
) {
    val id = state.previewId ?: return
    val scheme = MaterialTheme.colorScheme
    val name = state.nameOf(id)
    val isSelected = id in state.selected
    val atMaximum = !isSelected && state.selected.size >= MAX_DISTRICTS
    Surface(
        color = scheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)) {
            SheetHandle(onDismiss)
            if (atMaximum) {
                MaximumReachedContent(state, name, onChooseReplaceTarget, onConfirmReplace, onDismiss)
            } else {
                Text(name, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                val types = state.eventsByDistrict[id]?.types.orEmpty()
                if (types.isNotEmpty()) {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) { types.forEach { EventTypeLabel(it) } }
                }
                FlowRow(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Adding is the main button below the sheet.
                    if (isSelected) {
                        HackappSmallTonalButton(stringResource(R.string.action_remove), onRemove)
                    }
                    HackappSmallTextButton(stringResource(R.string.district_choose_another), onDismiss)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaximumReachedContent(
    state: DistrictPickUiState,
    name: String,
    onChooseReplaceTarget: (String) -> Unit,
    onConfirmReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val cityPack = LocalCityPack.current
    val language = LocalConfiguration.current.locales[0].language
    val plural = cityPack.labels[LabelKey.DISTRICT_PLURAL]?.get(language)
        ?: stringResource(R.string.district_pick_title).lowercase()
    Text(
        stringResource(R.string.district_max_title, MAX_DISTRICTS, plural),
        style = MaterialTheme.typography.titleMedium,
        color = scheme.onSurface,
    )
    Text(
        stringResource(R.string.district_max_body, name),
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    Column(Modifier.selectableGroup()) {
        state.selected.forEachIndexed { index, selectedId ->
            RadioOptionRow(
                label = stringResource(R.string.district_order_chip, index + 1, state.nameOf(selectedId)),
                selected = selectedId == state.replaceTargetId,
                onClick = { onChooseReplaceTarget(selectedId) },
            )
        }
    }
    FlowRow(
        Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HackappSmallTonalButton(
            text = stringResource(R.string.district_replace_action),
            onClick = onConfirmReplace,
            enabled = state.replaceTargetId != null,
        )
        HackappSmallTextButton(stringResource(R.string.action_cancel), onCancel)
    }
}

@Composable
private fun SheetHandle(onDismiss: () -> Unit) {
    val threshold = with(LocalDensity.current) { 32.dp.toPx() }
    var dragged by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(onDismiss) {
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged > threshold) onDismiss() },
                ) { change, amount ->
                    change.consume()
                    dragged += amount
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/** A read-only Event type. It is a label, so it does not look or act like a filter. */
@Composable
private fun EventTypeLabel(type: StopType) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            stopTypeLabel(type),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private val PreviewEvents = mapOf(
    "a" to DistrictEvents(14, listOf(StopType.Music, StopType.Family)),
    "b" to DistrictEvents(8, listOf(StopType.Cinema, StopType.Comedy)),
)

@Composable
private fun PreviewPage(state: DistrictPickUiState) = HackappTheme {
    DistrictPickPage(state, {}, {}, {}, {}, {}, {}, {}, {}, {})
}

@Preview(name = "District pick", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun DistrictPickPreview() = PreviewPage(
    DistrictPickUiState(
        loading = false,
        map = PreviewDistrictMap,
        eventsByDistrict = PreviewEvents,
        selected = listOf("a"),
        previewId = "b",
    ),
)

@Preview(name = "District pick · maximum", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun DistrictPickMaximumPreview() = PreviewPage(
    DistrictPickUiState(
        loading = false,
        map = PreviewDistrictMap,
        eventsByDistrict = PreviewEvents + ("c" to DistrictEvents(2, listOf(StopType.Sport))),
        selected = listOf("a", "b", "c"),
        previewId = "d",
        replaceTargetId = "b",
    ),
)

@Preview(name = "District pick · loading", showBackground = true, widthDp = 412, heightDp = 500)
@Composable
private fun DistrictPickLoadingPreview() = PreviewPage(DistrictPickUiState())

@Preview(name = "District pick · error", showBackground = true, widthDp = 412, heightDp = 500)
@Composable
private fun DistrictPickErrorPreview() = PreviewPage(DistrictPickUiState(loading = false, loadFailed = true))
