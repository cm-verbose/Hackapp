package com.example.hackapp.scaffold.screens.passport

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.example.hackapp.scaffold.screens.home.bottombar.MainBottomBar
import kotlinx.serialization.Serializable

@Serializable
object ScreenPassportDestination;

@Composable
fun ScreenPassport(controller: NavHostController, outerPaddingValues: PaddingValues) {
    Scaffold(bottomBar = { MainBottomBar(controller, outerPaddingValues) }) { paddingValues ->
        Box(modifier = Modifier.consumeWindowInsets(paddingValues)) {
            androidx.compose.material3.Text("Passport")
        }
    }
}
