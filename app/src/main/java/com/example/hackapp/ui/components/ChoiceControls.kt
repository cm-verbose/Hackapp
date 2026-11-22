package com.example.hackapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme

/** One option of a choice control. A disabled option stays visible but cannot be picked. */
data class ChoiceOption<T>(
    val value: T,
    val label: String,
    val enabled: Boolean = true,
    val icon: ImageVector? = null,
)

/** Figma "Filter chip" group, multi-select. Used for Interests and cuisines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> MultiChoiceChips(
    options: List<ChoiceOption<T>>,
    selected: Set<T>,
    onSelectedChange: (Set<T>) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            HackappChoiceChip(option, option.value in selected) {
                onSelectedChange(if (option.value in selected) selected - option.value else selected + option.value)
            }
        }
    }
}

/**
 * Figma "Filter chip" group, single-select: age range, budget, meal time.
 * With [allowDeselect], tapping the selected chip clears it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SingleChoiceChips(
    options: List<ChoiceOption<T>>,
    selected: T?,
    onSelectedChange: (T?) -> Unit,
    modifier: Modifier = Modifier,
    allowDeselect: Boolean = false,
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option.value == selected
            HackappChoiceChip(option, isSelected) {
                when {
                    !isSelected -> onSelectedChange(option.value)
                    allowDeselect -> onSelectedChange(null)
                }
            }
        }
    }
}

/** Selection is shown by colour only, so choosing an option never changes the chip's width. */
@Composable
private fun <T> HackappChoiceChip(option: ChoiceOption<T>, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = option.enabled,
        label = { Text(option.label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = option.icon?.let { icon ->
            { Icon(icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        },
    )
}

/** Figma "Segmented button": day/night, transit/Uber, language, Passport city. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> HackappSegmentedButton(
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelectedChange: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Labels may wrap to two lines; the row takes the tallest segment's height and the others fill it.
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option.value == selected,
                onClick = { onSelectedChange(option.value) },
                enabled = option.enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = Modifier.fillMaxHeight(),
                icon = {},
                label = {
                    Text(option.label, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                },
            )
        }
    }
}

@Preview(name = "Choice controls", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun ChoiceControlsPreview() = HackappTheme {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SingleChoiceChips(
            options = listOf("18", "25", "35", "45", "55").map { ChoiceOption(it, "$it+") },
            selected = "18",
            onSelectedChange = {},
        )
        MultiChoiceChips(
            options = listOf(
                ChoiceOption("music", stringResource(R.string.stop_type_music)),
                ChoiceOption("comedy", stringResource(R.string.stop_type_comedy)),
                ChoiceOption("cinema", stringResource(R.string.stop_type_cinema), enabled = false),
            ),
            selected = setOf("music"),
            onSelectedChange = {},
        )
        HackappSegmentedButton(
            options = listOf(
                ChoiceOption(true, stringResource(R.string.planning_day_out)),
                ChoiceOption(false, stringResource(R.string.planning_night_out)),
            ),
            selected = true,
            onSelectedChange = {},
        )
    }
}
