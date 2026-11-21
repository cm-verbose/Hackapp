package com.example.hackapp.scaffold.screens.screenA

import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.hackapp.auth.AuthHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object ScreenADestination

@Composable
fun ScreenA(onNavigateB: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    Log.d("AuthConsumer", context.toString());
    Log.d("AuthConsumer", activity.toString());

    val scope = CoroutineScope(Dispatchers.Main)
    val authHandler = AuthHandler(context, activity!!)

    Column() {
        Button(onClick = {
            scope.launch {
                authHandler.signIn()
            }
        }) {
            Text("Login")
        }

        Button(onClick = {
            onNavigateB()
        }) {
            Text("To B")
        }
    }
}
