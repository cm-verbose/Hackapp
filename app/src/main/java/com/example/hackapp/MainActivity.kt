package com.example.hackapp

import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.collection.intSetOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackapp.scaffold.AppScaffold
import com.example.hackapp.ui.theme.AndroidTemplateTheme
import com.example.hackapp.viewmodel.EventsUiState
import com.example.hackapp.viewmodel.montreal.MontrealEventsViewModel
import com.example.hackapp.viewmodel.toronto.TorontoEventsViewModel
import com.example.hackapp.widgets.AppWidgetReceiver
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        setContent {
            AndroidTemplateTheme {
                Column {
                    // TODO: temporary API test buttons, remove once events are wired into the UI
                    TempEventApiButtons()
                    Box(Modifier.weight(1f)) {
                        AppScaffold()
                    }
                }
            }
        }
        this.instantiateWidgets()
    }

    /** Instantiates app widgets. */
    private fun instantiateWidgets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            lifecycleScope.launch {
                val manager = GlanceAppWidgetManager(this@MainActivity)
                manager.setWidgetPreviews(
                    AppWidgetReceiver::class,
                    intSetOf(WIDGET_CATEGORY_HOME_SCREEN)
                )
            }
        }
    }
}

/** Temporary debug panel to trigger the event APIs. Results are also logged (tags TorontoEventsVM / MontrealEventsVM). */
@Composable
private fun TempEventApiButtons(
    torontoViewModel: TorontoEventsViewModel = viewModel(),
    montrealViewModel: MontrealEventsViewModel = viewModel(),
) {
    val torontoState by torontoViewModel.state.collectAsState()
    val montrealState by montrealViewModel.state.collectAsState()

    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { torontoViewModel.loadEvents() }) { Text("Toronto") }
                Button(onClick = { torontoViewModel.loadCityCsvEvents() }) { Text("TO City CSV") }
                Button(onClick = { torontoViewModel.loadAllEvents() }) { Text("TO All") }
                Button(onClick = { montrealViewModel.loadHasThingsEvents() }) { Text("MTL Has Things") }
                Button(onClick = { montrealViewModel.loadOpenDataEvents() }) { Text("MTL Open Data") }
                Button(onClick = { montrealViewModel.loadAllEvents() }) { Text("MTL All") }
            }
            Text("Toronto: ${torontoState.summary()}", style = MaterialTheme.typography.bodySmall)
            Text("Montréal: ${montrealState.summary()}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun EventsUiState.summary(): String = when (this) {
    EventsUiState.Idle -> "idle"
    EventsUiState.Loading -> "loading…"
    is EventsUiState.Success -> "${events.size} events" +
        (events.firstOrNull()?.let { " — first: ${it.title} @ ${it.schedule.startDate}" } ?: "")
    is EventsUiState.Error -> "error: $message"
}
