package com.example.hackapp.scaffold.screens.planning

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackapp.viewmodel.montreal.MontrealEventsViewModel
import com.example.hackapp.viewmodel.toronto.TorontoEventsViewModel
import kotlinx.serialization.Serializable

@Serializable
object PlanningScreenDestination

@Composable
fun ScreenPlanning(onLogOff: () -> Unit) {
    // Activity-scoped so the same instances are shared with the rest of the app.
    val activity = LocalActivity.current as ComponentActivity
    val torontoViewModel: TorontoEventsViewModel = viewModel(viewModelStoreOwner = activity)
    val montrealViewModel: MontrealEventsViewModel = viewModel(viewModelStoreOwner = activity)

    // Main page load: read events from Firestore, or fetch from the APIs and store them on first run.
    LaunchedEffect(Unit) {
        torontoViewModel.ensureLoaded()
        montrealViewModel.ensureLoaded()
    }

    GoogleLogoffButton(onLogOff = onLogOff)
}
