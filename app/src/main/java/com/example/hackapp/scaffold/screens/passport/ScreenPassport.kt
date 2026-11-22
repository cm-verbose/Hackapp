package com.example.hackapp.scaffold.screens.passport

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.example.hackapp.scaffold.composables.mainbottombar.MainBottomBar
import com.example.hackapp.scaffold.sandbox.PhysicsScreen
import com.example.hackapp.scaffold.screens.passport.gravityLock.SimulationGravityLockButton
import kotlinx.serialization.Serializable

@Serializable
object ScreenPassportDestination

@Composable
fun ScreenPassport(controller: NavHostController, outerPaddingValues: PaddingValues) {
    var isGravityLocked by rememberSaveable { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        bottomBar = { MainBottomBar(controller, outerPaddingValues) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            SimulationGravityLockButton(
                isLocked = isGravityLocked,
                onLockedChange = { isGravityLocked = it },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            // Sharing stays off here so opening the Passport never asks for a permission.
            PhysicsScreen(
                modifier = Modifier.fillMaxSize(),
                gravityLocked = isGravityLocked,
                shareWithNearby = false,
                snackbarHostState = snackbarHostState,
            )
        }
    }
}
