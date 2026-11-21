package com.example.hackapp.scaffold.screens.planning

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable

@Serializable
object PlanningScreenDestination

@Composable
fun ScreenPlanning(onLogOff: () -> Unit) {
    GoogleLogoffButton(onLogOff = onLogOff)
}
