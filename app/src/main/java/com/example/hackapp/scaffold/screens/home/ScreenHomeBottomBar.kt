package com.example.hackapp.scaffold.screens.home

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.hackapp.R

val destinations: List<Pair<String, ImageVector>> = listOf(
    Pair("Acceuil", Icons.Default.Home),
    Pair("Itinéraires", Icons.Default.Route),
)
val destinationPassport: List<Pair<String, Int>> = listOf(
    Pair("Passeport", R.drawable.ic_passport)
)

/** Home screen bottom app bar. */
@Composable
fun ScreenHomeBottomAppBar(padding: PaddingValues) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    BottomAppBar(
        contentPadding = PaddingValues(0.dp),
        windowInsets = WindowInsets(bottom = padding.calculateBottomPadding())
    ) {
        PrimaryTabRow(
            selectedIndex,
            modifier = Modifier
                .fillMaxSize()
        ) {
            destinations.forEachIndexed { index, pair ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { selectedIndex = index },
                    text = { Text(pair.first) },
                    icon = {
                        Icon(
                            imageVector = pair.second,
                            contentDescription = pair.first
                        )
                    }
                )
            }
            destinationPassport.forEachIndexed { index, pair ->
                val resource = painterResource(pair.second)
                Tab(
                    selected = selectedIndex == index + destinations.size,
                    onClick = { selectedIndex = index + destinations.size },
                    text = { Text(pair.first) },
                    icon = {
                        Icon(
                            painter = resource,
                            contentDescription = pair.first
                        )
                    }
                )
            }
        }
    }
}
