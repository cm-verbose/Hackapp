package com.example.hackapp.scaffold.screens.planning

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.google.firebase.auth.FirebaseAuth

/** Log off button for the user */
@Composable
fun GoogleLogoffButton(onLogOff: () -> Unit) {
    Button(onClick = {
        FirebaseAuth.getInstance().signOut()
        onLogOff()
    }) {
        Text("Log off")
    }
}
