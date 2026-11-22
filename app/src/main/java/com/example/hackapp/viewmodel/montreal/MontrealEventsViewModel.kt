package com.example.hackapp.viewmodel.montreal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hackapp.data.Event
import com.example.hackapp.data.remote.hasthings.HasThingsCity
import com.example.hackapp.data.remote.hasthings.HasThingsEventSource
import com.example.hackapp.data.remote.montreal.MontrealOpenDataEventSource
import com.example.hackapp.data.repository.FirestoreEventCache
import com.example.hackapp.viewmodel.EventsUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Montréal events, from two sources (each cached in Firestore after the first fetch):
 * - montreal.hasthings.com (concerts, shows, nightlife…)
 * - donnees.montreal.ca "Événements publics" (city-run events)
 */
class MontrealEventsViewModel : ViewModel() {

    private val hasThings = HasThingsEventSource(HasThingsCity.MONTREAL)
    private val openData = MontrealOpenDataEventSource()
    private val cache = FirestoreEventCache()

    private val _state = MutableStateFlow<EventsUiState>(EventsUiState.Idle)
    val state: StateFlow<EventsUiState> = _state.asStateFlow()

    fun loadHasThingsEvents() = load("Has Things") { hasThingsEvents() }

    fun loadOpenDataEvents() = load("open data") { openDataEvents() }

    /** Loads both sources in parallel and merges them. */
    fun loadAllEvents() = load("all sources") {
        val a = async { hasThingsEvents() }
        val b = async { openDataEvents() }
        a.await() + b.await()
    }

    /** Loads everything unless already loaded/loading (safe to call on every screen entry). */
    fun ensureLoaded() {
        if (_state.value is EventsUiState.Idle || _state.value is EventsUiState.Error) loadAllEvents()
    }

    private suspend fun hasThingsEvents() =
        cache.getOrFetch(FirestoreEventCache.MONTREAL_HAS_THINGS) { hasThings.fetchEvents() }

    private suspend fun openDataEvents() =
        cache.getOrFetch(FirestoreEventCache.MONTREAL_OPEN_DATA) { openData.fetchEvents() }

    private fun load(label: String, fetch: suspend CoroutineScope.() -> List<Event>) {
        _state.value = EventsUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val events = coroutineScope { fetch() }
                Log.d(TAG, "Loaded ${events.size} Montréal events ($label)")
                EventsUiState.Success(events)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Montréal events ($label)", e)
                EventsUiState.Error(e.message ?: e::class.java.simpleName)
            }
        }
    }

    private companion object {
        const val TAG = "MontrealEventsVM"
    }
}
