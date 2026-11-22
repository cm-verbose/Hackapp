package com.example.hackapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme

/** Figma "HeroHeader" (53:1256): big title on the hero colour (e.g. Passport stamp count). */
@Composable
fun HeroHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val colors = HackappTheme.appColors
    Column(
        modifier
            .clip(RoundedCornerShape(HackappCorners.ExtraLarge))
            .background(colors.hero)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = colors.onHero, modifier = Modifier.fillMaxWidth())
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = colors.onHeroCard, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "HeroHeader", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun HeroHeaderPreview() = HackappTheme {
    HeroHeader(
        title = pluralStringResource(R.plurals.stamps_count, 12, 12),
        subtitle = pluralStringResource(R.plurals.districts_count, 4, 4) + " · Montréal",
    )
}
