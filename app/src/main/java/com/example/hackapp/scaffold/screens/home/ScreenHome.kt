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
fun ScreenHome(outerPaddingValues: PaddingValues) {
    Scaffold(bottomBar = {
        ScreenHomeBottomAppBar(outerPaddingValues)
    }) { paddingValues ->
        Box(modifier = Modifier.consumeWindowInsets(paddingValues)) {

        }
    }
}
