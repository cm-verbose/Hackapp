package com.example.hackapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.citypack.rememberDistrictMap
import com.example.hackapp.data.model.PriceRange
import com.example.hackapp.data.model.VenueType
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.scaffold.screens.planning.MealOptionRow
import com.example.hackapp.scaffold.screens.planning.MealPickerUiState
import com.example.hackapp.scaffold.screens.planning.MealPrice
import com.example.hackapp.scaffold.screens.planning.MealTab
import com.example.hackapp.ui.theme.HackappTheme
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val ClockFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Figma "MealPickerSheet" (141:2520): choose a Meal for a reserved Meal Block, or skip it.
 * The next District's name comes from the City Pack. [budgetLabel] is the Meal budget bracket
 * label (for example `budget_under_50`); null leaves the budget out of the subtitle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPickerSheet(
    state: MealPickerUiState,
    onSelectTab: (MealTab) -> Unit,
    onChoose: (placeId: Long) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    budgetLabel: String? = null,
) {
    val map by rememberDistrictMap()
    val nextDistrictName = state.nextDistrictId?.let { id -> map?.districts?.firstOrNull { it.id == id }?.name }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        MealPickerContent(
            state = state,
            nextDistrictName = nextDistrictName,
            budgetLabel = budgetLabel,
            onSelectTab = onSelectTab,
            onChoose = onChoose,
            onSkip = onSkip,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

/** Sheet body without the ModalBottomSheet, for Selection and previews. */
@Composable
fun MealPickerContent(
    state: MealPickerUiState,
    nextDistrictName: String?,
    budgetLabel: String?,
    onSelectTab: (MealTab) -> Unit,
    onChoose: (placeId: Long) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val timeRange = stringResource(
        R.string.selection_time_block,
        state.blockStart.format(ClockFormat),
        state.blockEnd.format(ClockFormat),
    )
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                mealPickerTitle(state.kind),
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                if (budgetLabel != null) stringResource(R.string.meal_picker_subtitle, timeRange, budgetLabel) else timeRange,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }

        // Tabs have nothing to switch between when both are empty.
        if (state.tabs.size > 1 && !state.hasNoOptions) {
            HackappSegmentedButton(
                options = state.tabs.map { tab ->
                    ChoiceOption(
                        tab,
                        when (tab) {
                            MealTab.Here -> stringResource(R.string.meal_tab_here)
                            MealTab.NextDistrict -> nextDistrictName
                                ?.let { stringResource(R.string.meal_tab_in_district, it) }
                                ?: stringResource(R.string.meal_tab_next)
                        },
                    )
                },
                selected = state.selectedTab,
                onSelectedChange = onSelectTab,
                // Long borough names such as Rivière-des-Prairies–Pointe-aux-Trembles need a third line.
                maxLines = 3,
            )
        }

        when {
            state.hasNoOptions -> EmptyState(title = stringResource(R.string.meal_picker_no_options))
            state.isEmpty -> EmptyState(
                title = when (state.selectedTab) {
                    MealTab.Here -> stringResource(R.string.meal_picker_empty_here)
                    MealTab.NextDistrict -> nextDistrictName
                        ?.let { stringResource(R.string.meal_picker_empty_next, it) }
                        ?: stringResource(R.string.state_empty_title)
                },
            )
            else -> MealOptionList(state.options, onChoose)
        }

        val skipLabel = stringResource(R.string.action_skip_meal)
        if (state.hasNoOptions) {
            HackappPrimaryButton(skipLabel, onSkip, Modifier.align(Alignment.CenterHorizontally))
        } else {
            HackappSmallTextButton(skipLabel, onSkip, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun ColumnScope.MealOptionList(options: List<MealOptionRow>, onChoose: (Long) -> Unit) {
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
    ) {
        options.forEach { row -> MealOptionRow(row) { onChoose(row.placeId) } }
    }
}

/** Localized label of a venue type (strings.xml `venue_type_*`). */
@Composable
fun venueTypeLabel(type: VenueType): String = stringResource(
    when (type) {
        VenueType.RESTAURANT -> R.string.venue_type_restaurant
        VenueType.FAST_FOOD -> R.string.venue_type_fast_food
        VenueType.CAFE -> R.string.venue_type_cafe
        VenueType.BAKERY -> R.string.venue_type_bakery
        VenueType.DESSERT -> R.string.venue_type_dessert
        VenueType.FOOD_TRUCK -> R.string.venue_type_food_truck
        VenueType.BAR -> R.string.venue_type_bar
    },
)

/** One Meal option: name, venue type, walking distance when known, and the price range or "Price unknown" muted. */
@Composable
private fun MealOptionRow(row: MealOptionRow, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.name, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                Text(venueTypeLabel(row.venueType), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                val metres = row.distanceMeters
                val minutes = row.walkingMinutes
                if (metres != null && minutes != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        HackappIconView(HackappIcon.DirectionsWalk, size = 16.dp, tint = scheme.onSurfaceVariant)
                        Text(
                            stringResource(R.string.meal_picker_walk, metres, minutes),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            when (val price = row.price) {
                is MealPrice.Range -> Text(
                    formatCadRange(price.range.min, price.range.max),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    textAlign = TextAlign.End,
                )
                MealPrice.Unknown -> Text(
                    stringResource(R.string.meal_picker_price_unknown),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
        HorizontalDivider(color = scheme.outlineVariant)
    }
}

@Composable
private fun mealPickerTitle(kind: MealKind): String = stringResource(
    when (kind) {
        MealKind.Lunch -> R.string.meal_picker_title_lunch
        MealKind.Dinner -> R.string.meal_picker_title_dinner
        MealKind.LateBite -> R.string.meal_picker_title_late_bite
    },
)

// Previews

private val PreviewZone: ZoneId = ZoneId.of("America/Toronto")

private fun previewRow(
    id: Long,
    name: String,
    metres: Int?,
    minutes: Int?,
    price: MealPrice,
    next: Boolean = false,
    venueType: VenueType = VenueType.RESTAURANT,
) =
    MealOptionRow(
        placeId = id,
        name = name,
        venueType = venueType,
        distanceMeters = metres,
        walkingMinutes = minutes,
        price = price,
        inNextDistrict = next,
        matchesCuisine = false,
    )

private val PreviewHere = listOf(
    previewRow(1, "Le Cartet", 120, 2, MealPrice.Range(PriceRange(10, 20, false), 20)),
    previewRow(2, "Café Olimpico", 300, 4, MealPrice.Range(PriceRange(10, 20, false), 20), venueType = VenueType.CAFE),
    previewRow(3, "Schwartz’s Deli", 450, 6, MealPrice.Range(PriceRange(20, 30, false), 30)),
    previewRow(4, "Chez Tousignant", 520, 7, MealPrice.Unknown),
    previewRow(5, "Toqué! Restaurant gastronomique", null, null, MealPrice.Range(PriceRange(100, null, false), null)),
)

private fun previewState(
    here: List<MealOptionRow> = PreviewHere,
    next: List<MealOptionRow> = listOf(previewRow(6, "Brasserie T", 900, 12, MealPrice.Range(PriceRange(20, 30, false), 30), next = true)),
    selectedTab: MealTab = MealTab.Here,
    nextDistrictId: String? = "ville-marie",
) = MealPickerUiState(
    blockId = "lunch",
    kind = MealKind.Lunch,
    blockStart = ZonedDateTime.of(2026, 11, 28, 12, 0, 0, 0, PreviewZone),
    blockEnd = ZonedDateTime.of(2026, 11, 28, 13, 30, 0, 0, PreviewZone),
    tabs = if (nextDistrictId != null) listOf(MealTab.Here, MealTab.NextDistrict) else listOf(MealTab.Here),
    selectedTab = selectedTab,
    nextDistrictId = nextDistrictId,
    here = here,
    nextDistrict = next,
)

@Composable
private fun MealPickerPreview(
    state: MealPickerUiState,
    budgetLabel: String? = stringResource(R.string.budget_under_50),
    nextDistrictName: String? = "Ville-Marie",
) = HackappTheme {
    MealPickerContent(
        state = state,
        nextDistrictName = nextDistrictName,
        budgetLabel = budgetLabel,
        onSelectTab = {},
        onChoose = {},
        onSkip = {},
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Preview(name = "MealPicker · two tabs", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP, heightDp = 640)
@Composable
private fun MealPickerTwoTabsPreview() = MealPickerPreview(previewState())

@Preview(name = "MealPicker · one tab, no budget", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP, heightDp = 640)
@Composable
private fun MealPickerOneTabPreview() = MealPickerPreview(previewState(nextDistrictId = null), budgetLabel = null)

@Preview(name = "MealPicker · next District name unknown", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP, heightDp = 640)
@Composable
private fun MealPickerUnknownDistrictPreview() = MealPickerPreview(previewState(), nextDistrictName = null)

@Preview(name = "MealPicker · longest District name, 360 dp, 1.3x", showBackground = true, widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Composable
private fun MealPickerLongestDistrictPreview() =
    MealPickerPreview(previewState(), nextDistrictName = "Rivière-des-Prairies–Pointe-aux-Trembles")

@Preview(name = "MealPicker · empty next tab", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP, heightDp = 400)
@Composable
private fun MealPickerEmptyNextPreview() = MealPickerPreview(previewState(next = emptyList(), selectedTab = MealTab.NextDistrict))

@Preview(name = "MealPicker · no options", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP, heightDp = 400)
@Composable
private fun MealPickerNoOptionsPreview() = MealPickerPreview(previewState(here = emptyList(), next = emptyList()))

@Preview(name = "MealPicker · 360 dp, 1.3x", showBackground = true, widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Composable
private fun MealPickerLargeTextPreview() = MealPickerPreview(previewState())
