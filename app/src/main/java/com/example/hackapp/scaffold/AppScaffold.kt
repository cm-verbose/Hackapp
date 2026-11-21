package com.example.hackapp.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.hackapp.scaffold.navigation.AppNavigation

/**
 * The [AppScaffold] serves as the root of the UI as to isolate the
 * [MainActivity][com.example.hackapp.MainActivity] to its specific tasks.
 */
@Composable
fun AppScaffold() {
    val navController: NavHostController = rememberNavController()
    Scaffold { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AppNavigation(navController)
        }
    }
}
