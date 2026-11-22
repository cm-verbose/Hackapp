package com.example.hackapp.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme

/** The three top-level destinations of Figma's "Navigation Bar". */
enum class AppTab(@param:StringRes val labelRes: Int) {
    Home(R.string.nav_home),
    Itineraries(R.string.nav_itineraries),
    Passport(R.string.nav_passport),
}

@Composable
private fun AppTab.iconPainter(): Painter = when (this) {
    AppTab.Home -> rememberVectorPainter(Icons.Filled.Home)
    AppTab.Itineraries -> rememberVectorPainter(Icons.Filled.Route)
    AppTab.Passport -> painterResource(R.drawable.ic_passport)
}

@Composable
fun AppBottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier) {
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { if (tab != selected) onSelect(tab) },
                icon = { Icon(tab.iconPainter(), contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}

@Preview(name = "AppBottomBar", showBackground = true, widthDp = 412)
@Composable
private fun AppBottomBarPreview() = HackappTheme {
    AppBottomBar(selected = AppTab.Home, onSelect = {})
}
