package com.example.hackapp.scaffold.screens.screenB

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable

@Serializable
object ScreenBDestination

@Composable
fun ScreenB(onNavigateA: () -> Unit) {
    Button(onClick = {
        onNavigateA()
    }) {
        Text("To A")
    }
}
