package com.example.hackapp.scaffold.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.example.hackapp.scaffold.screens.home.bottombar.MainBottomBar
import kotlinx.serialization.Serializable

@Serializable
object ScreenHomeDestination

@Composable
fun ScreenHome(controller: NavHostController, outerPaddingValues: PaddingValues) {
    Scaffold(bottomBar = {
        MainBottomBar(controller, outerPaddingValues)
    }) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            Text("Accueil")
        }
    }
}
