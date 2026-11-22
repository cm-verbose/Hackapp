package com.example.hackapp.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.hackapp.scaffold.navigation.AppNavigation
import com.example.hackapp.scaffold.navigation.ShareItineraryRoute
import com.example.hackapp.scaffold.navigation.ShareStampRoute
import com.example.hackapp.scaffold.sandbox.share.ShareOffer
import com.example.hackapp.scaffold.screens.share.NearbyOfferPrompt

/**
 * The [AppScaffold] serves as the root of the UI as to isolate the
 * [MainActivity][com.example.hackapp.MainActivity] to its specific tasks.
 */
@Composable
fun AppScaffold() {
    val navController: NavHostController = rememberNavController()
    Scaffold { innerPadding ->
        AppNavigation(navController, innerPadding)
    }
    NearbyOfferPrompt { offer ->
        navController.navigate(
            when (offer) {
                ShareOffer.Stamp -> ShareStampRoute()
                ShareOffer.Itinerary -> ShareItineraryRoute()
            },
        ) { launchSingleTop = true }
    }
}
