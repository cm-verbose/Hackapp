package com.example.hackapp.scaffold.composables.mainbottombar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.hackapp.R
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination
import com.example.hackapp.scaffold.screens.itinerary.ScreenItineraryDestination
import com.example.hackapp.scaffold.screens.passport.ScreenPassportDestination

/** Home screen bottom app bar. */
@Composable
fun MainBottomBar(controller: NavHostController, padding: PaddingValues) {
    val navBackStackEntry by controller.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        NavigationItem.VectorItem(
            title = "Accueil",
            target = ScreenHomeDestination,
            targetClass = ScreenHomeDestination::class,
            icon = Icons.Default.Home
        ),
        NavigationItem.VectorItem(
            title = "Itinéraires",
            target = ScreenItineraryDestination,
            targetClass = ScreenItineraryDestination::class,
            icon = Icons.Default.Route
        ),
        NavigationItem.ResourceItem(
            title = "Passeport",
            target = ScreenPassportDestination,
            targetClass = ScreenPassportDestination::class,
            iconRes = R.drawable.ic_passport
        )
    )

    val selectedIndex = items.indexOfFirst { item ->
        currentDestination?.hasRoute(item.targetClass) == true
    }.coerceAtLeast(0)

    BottomAppBar(
        contentPadding = PaddingValues(0.dp),
        windowInsets = WindowInsets(bottom = padding.calculateBottomPadding())
    ) {
        PrimaryTabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier.fillMaxSize()
        ) {
            items.forEachIndexed { index, item ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = {
                        controller.navigate(item.target) {
                            popUpTo(controller.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    text = { Text(item.title) },
                    icon = {
                        Icon(
                            painter = item.painter(),
                            contentDescription = item.title
                        )
                    }
                )
            }
        }
    }
}
