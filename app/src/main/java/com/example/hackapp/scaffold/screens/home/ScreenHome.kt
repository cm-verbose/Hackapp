package com.example.hackapp.scaffold.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.serialization.Serializable

@Serializable
object ScreenHomeDestination

@Composable
fun ScreenHome(outerpaddingValues: PaddingValues) {
    Scaffold(bottomBar = {
        ScreenHomeBottomAppBar(outerpaddingValues)
    }) { paddingValues ->
        Box(modifier = Modifier.consumeWindowInsets(paddingValues)) {

        }
    }
}
