package com.example.hackapp.viewmodel.toronto

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hackapp.data.Event
import com.example.hackapp.data.local.TorontoCityCsvEventSource
import com.example.hackapp.data.remote.hasthings.HasThingsCity
import com.example.hackapp.data.remote.hasthings.HasThingsEventSource
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
 * Toronto events, from two sources (each cached in Firestore after the first fetch):
 * - toronto.hasthings.com
 * - the bundled City of Toronto CSV (treated like an API)
 */
class TorontoEventsViewModel(application: Application) : AndroidViewModel(application) {

    private val hasThings = HasThingsEventSource(HasThingsCity.TORONTO)
    private val cityCsv = TorontoCityCsvEventSource(application)
    private val cache = FirestoreEventCache()

    private val _state = MutableStateFlow<EventsUiState>(EventsUiState.Idle)
    val state: StateFlow<EventsUiState> = _state.asStateFlow()

    fun loadEvents() = load("Has Things") { hasThingsEvents() }

    fun loadCityCsvEvents() = load("city CSV") { cityCsvEvents() }

    /** Loads both sources in parallel and merges them. */
    fun loadAllEvents() = load("all sources") {
        val a = async { hasThingsEvents() }
        val b = async { cityCsvEvents() }
        a.await() + b.await()
    }

    /** Loads everything unless already loaded/loading (safe to call on every screen entry). */
    fun ensureLoaded() {
        if (_state.value is EventsUiState.Idle || _state.value is EventsUiState.Error) loadAllEvents()
    }

    private suspend fun hasThingsEvents() =
        cache.getOrFetch(FirestoreEventCache.TORONTO_HAS_THINGS) { hasThings.fetchEvents() }

    private suspend fun cityCsvEvents() =
        cache.getOrFetch(FirestoreEventCache.TORONTO_CITY_CSV) { cityCsv.fetchEvents() }

    private fun load(label: String, fetch: suspend CoroutineScope.() -> List<Event>) {
        _state.value = EventsUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val events = coroutineScope { fetch() }
                Log.d(TAG, "Loaded ${events.size} Toronto events ($label)")
                EventsUiState.Success(events)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Toronto events ($label)", e)
                EventsUiState.Error(e.message ?: e::class.java.simpleName)
            }
        }
    }

    private companion object {
        const val TAG = "TorontoEventsVM"
    }
}
