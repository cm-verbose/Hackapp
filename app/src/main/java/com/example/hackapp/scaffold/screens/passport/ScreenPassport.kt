package com.example.hackapp.scaffold.screens.passport

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.example.hackapp.scaffold.composables.mainbottombar.MainBottomBar
import com.example.hackapp.scaffold.screens.passport.sandbox.PassportSandbox
import kotlinx.serialization.Serializable

@Serializable
object ScreenPassportDestination

@Composable
fun ScreenPassport(controller: NavHostController, outerPaddingValues: PaddingValues) {
    Scaffold(bottomBar = { MainBottomBar(controller, outerPaddingValues) }) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            PassportSandbox(modifier = Modifier.fillMaxSize())
        }
    }
}
