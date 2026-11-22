package com.example.hackapp.scaffold.screens.home.bottombar

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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import com.example.hackapp.ui.motion.HackappMotion
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.hackapp.R
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination
import com.example.hackapp.scaffold.screens.itinerary.ScreenItineraryDestination
import com.example.hackapp.scaffold.screens.passport.ScreenPassportDestination
import kotlin.reflect.KClass

private sealed class NavigationItem(
    val title: String,
    val target: Any,
    val targetClass: KClass<*>
) {
    @Composable
    abstract fun painter(): Painter

    class VectorItem(
        title: String,
        target: Any,
        targetClass: KClass<*>,
        private val icon: ImageVector
    ) : NavigationItem(title, target, targetClass) {
        @Composable
        override fun painter(): Painter = rememberVectorPainter(icon)
    }

    class ResourceItem(
        title: String,
        target: Any,
        targetClass: KClass<*>,
        private val iconRes: Int
    ) : NavigationItem(title, target, targetClass) {
        @Composable
        override fun painter(): Painter = painterResource(iconRes)
    }
}

/** Home screen bottom app bar. */
@Composable
fun MainBottomBar(controller: NavHostController, padding: PaddingValues) {
    val items = listOf(
        NavigationItem.VectorItem(
            title = stringResource(R.string.nav_home),
            target = ScreenHomeDestination,
            targetClass = ScreenHomeDestination::class,
            icon = Icons.Default.Home
        ),
        NavigationItem.VectorItem(
            title = stringResource(R.string.nav_itineraries),
            target = ScreenItineraryDestination,
            targetClass = ScreenItineraryDestination::class,
            icon = Icons.Default.Route
        ),
        NavigationItem.ResourceItem(
            title = stringResource(R.string.nav_passport),
            target = ScreenPassportDestination,
            targetClass = ScreenPassportDestination::class,
            iconRes = R.drawable.ic_passport
        )
    )

    val navBackStackEntry by controller.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

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
                            // Login leaves the back stack after sign-in, so Home is the tab root.
                            popUpTo(ScreenHomeDestination) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    text = { Text(item.title) },
                    icon = {
                        val lift by animateFloatAsState(if (selectedIndex == index) 1.18f else 1f, HackappMotion.bouncy(), label = "tabIcon")
                        Icon(
                            painter = item.painter(),
                            contentDescription = item.title,
                            modifier = Modifier.graphicsLayer { scaleX = lift; scaleY = lift },
                        )
                    }
                )
            }
        }
    }
}
