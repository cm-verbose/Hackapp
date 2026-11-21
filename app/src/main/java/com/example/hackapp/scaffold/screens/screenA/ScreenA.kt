package com.example.hackapp.scaffold.screens.screenA

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable

@Serializable
object ScreenADestination

@Composable
fun ScreenA(onNavigateB: () -> Unit) {
    Button(onClick = {
        onNavigateB()
    }) {
        Text("To B")
    }
}
