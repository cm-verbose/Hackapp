package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma "DistrictPreviewSheet" (53:1340), PlanningDistrictPickScreen: content of the bottom
 * sheet shown when a District is tapped on [DistrictMap]. Wrap it in a ModalBottomSheet.
 *
 * [tags] are the District's stop types (M3 FilterChip, per Figma Code Connect).
 * [position] is the visit order this District would take (1–3).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DistrictPreviewSheet(
    districtName: String,
    description: String,
    tags: List<String>,
    position: Int,
    onAdd: () -> Unit,
    onChooseAnother: () -> Unit,
    modifier: Modifier = Modifier,
    selectedTags: Set<String> = emptySet(),
    onTagClick: (String) -> Unit = {},
    addLabel: String = stringResource(R.string.district_add_as_position, position),
    chooseAnotherLabel: String = stringResource(R.string.district_choose_another),
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(HackappCorners.ExtraLarge))
            .background(scheme.surfaceContainerLow)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(districtName, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.fillMaxWidth())
        Text(description, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                val selected = tag in selectedTags
                FilterChip(
                    selected = selected,
                    onClick = { onTagClick(tag) },
                    label = { Text(tag, style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HackappSmallTonalButton(text = addLabel, onClick = onAdd)
            HackappSmallTextButton(text = chooseAnotherLabel, onClick = onChooseAnother)
        }
    }
}

@Preview(name = "DistrictPreviewSheet", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun DistrictPreviewSheetPreview() = HackappTheme {
    DistrictPreviewSheet(
        districtName = "Rosemont–La Petite-Patrie",
        description = "Marchés, cafés et petites salles de spectacle.",
        tags = listOf(stringResource(R.string.stop_type_music), "Marché", stringResource(R.string.stop_type_family)),
        position = 3,
        onAdd = {},
        onChooseAnother = {},
    )
}
