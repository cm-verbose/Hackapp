package com.example.hackapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.ui.theme.HackappTheme

/** A Stamp ready for display. */
data class StampUi(val id: String, val name: String, val date: String, val district: String)

/** Figma Passport section: a District heading and its Stamps. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PassportDistrictSection(
    districtName: String,
    stamps: List<StampUi>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(districtName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            stamps.forEach { stamp ->
                Stamp(
                    initials = stampInitials(stamp.name),
                    stampName = stamp.name,
                    date = stamp.date,
                    district = stamp.district,
                )
            }
        }
    }
}

@Preview(name = "PassportDistrictSection", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun PassportDistrictSectionPreview() = HackappTheme {
    PassportDistrictSection(
        districtName = "Le Plateau-Mont-Royal",
        stamps = listOf(
            StampUi("1", "Jazz sur le toit", "28 nov. 2026", "Le Plateau"),
            StampUi("2", "Bistro du coin", "28 nov. 2026", "Le Plateau"),
            StampUi("3", "Café de la rue", "14 nov. 2026", "Le Plateau"),
        ),
    )
}
