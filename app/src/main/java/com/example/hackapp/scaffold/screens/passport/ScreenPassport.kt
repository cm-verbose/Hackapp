package com.example.hackapp.scaffold.screens.passport

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.example.hackapp.R
import com.example.hackapp.scaffold.composables.mainbottombar.MainBottomBar
import kotlinx.serialization.Serializable

@Serializable
object ScreenPassportDestination

@Composable
fun ScreenPassport(controller: NavHostController, outerPaddingValues: PaddingValues) {
    Scaffold(bottomBar = { MainBottomBar(controller, outerPaddingValues) }) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            Text(stringResource(R.string.passport_title))
        }
    }
}
