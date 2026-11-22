package com.example.hackapp.scaffold.screens.underway

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hackapp.scaffold.screens.onboarding.LoadState
import com.example.hackapp.underway.EndOfDay
import com.example.hackapp.underway.Rejection
import com.example.hackapp.underway.UnderwayClock
import com.example.hackapp.underway.UnderwayCoordinator
import com.example.hackapp.underway.UnderwayEvent
import com.example.hackapp.underway.UnderwayPhase
import com.example.hackapp.underway.UnderwayView
import kotlinx.coroutines.CancellationException
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private const val MINUTE_MILLIS = 60_000L

/** What every Underway page draws. [now] comes from the injected clock and moves each minute. */
data class UnderwayScreenData(val view: UnderwayView, val now: Instant)

/**
 * State of the Itinerary that is Underway, for every Underway page.
 *
 * [state] is `Loading` until the first read, `Failed` when nothing is Underway or storage failed.
 * Once the day ends, [state] keeps the last value and [endOfDay] loads: navigate to End of day when
 * it is `Ready`. A minute ticker reads [clock], so a demo clock drives it too.
 */
class UnderwayViewModel(
    private val uid: String,
    private val coordinator: UnderwayCoordinator,
    private val clock: UnderwayClock,
    private val notificationsAllowed: () -> Boolean,
) : ViewModel() {
    private val _view = MutableStateFlow<LoadState<UnderwayScreenData>>(LoadState.Loading)
    val state: StateFlow<LoadState<UnderwayScreenData>> = _view.asStateFlow()

    private val _endOfDay = MutableStateFlow<LoadState<EndOfDay>>(LoadState.Loading)
    val endOfDay: StateFlow<LoadState<EndOfDay>> = _endOfDay.asStateFlow()

    private val _rejections = MutableSharedFlow<Rejection>(extraBufferCapacity = 4)
    /** Why an action changed nothing, such as `NeedsConfirmation` for End Itinerary. */
    val rejections: SharedFlow<Rejection> = _rejections.asSharedFlow()

    private var lastItineraryId: String? = null

    init {
        viewModelScope.launch {
            coordinator.observeView(uid, notificationsAllowed)
                .catch { _view.value = LoadState.Failed }
                .collect { apply(it) }
        }
        viewModelScope.launch {
            while (true) {
                delay(MINUTE_MILLIS - clock.now().toEpochMilli().mod(MINUTE_MILLIS))
                refresh()
            }
        }
    }

    /** Call when the screen resumes: reschedules alerts, fires any due, then refreshes. */
    fun onResume() {
        viewModelScope.launch {
            try {
                coordinator.onAppOpen(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The view below still shows what is stored.
            }
            refresh()
        }
    }

    fun dispatch(event: UnderwayEvent) {
        val id = lastItineraryId ?: return
        viewModelScope.launch {
            try {
                val result = coordinator.dispatch(uid, id, event, notificationsAllowed())
                result.rejection?.let { _rejections.emit(it) }
                apply(result.view, ended = result.view?.phase == UnderwayPhase.Ended)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _view.value = LoadState.Failed
            }
        }
    }

    /** Loads or reloads End of day. Call again after a failure: it retries awarding Stamps. */
    fun retry() {
        val id = lastItineraryId ?: return
        viewModelScope.launch {
            _endOfDay.value = try {
                coordinator.endOfDay(uid, id)?.let { LoadState.Ready(it) } ?: LoadState.Failed
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                LoadState.Failed
            }
        }
    }

    private suspend fun refresh() {
        try {
            apply(coordinator.view(uid, notificationsAllowed()))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _view.value = LoadState.Failed
        }
    }

    private fun apply(view: UnderwayView?, ended: Boolean = false) {
        if (view != null) {
            lastItineraryId = view.itineraryId
            _view.value = LoadState.Ready(UnderwayScreenData(view, clock.now()))
            if (ended) retry()
        } else if (lastItineraryId != null) {
            // It was Underway and is not any more: the day ended.
            if (_endOfDay.value !is LoadState.Ready) retry()
        } else {
            _view.value = LoadState.Failed
        }
    }

    companion object {
        /** Use inside a Composable: `viewModel(factory = UnderwayViewModel.factory(...))`. */
        @Composable
        fun create(
            uid: String,
            coordinator: UnderwayCoordinator,
            clock: UnderwayClock,
            notificationsAllowed: () -> Boolean,
        ): UnderwayViewModel = viewModel(factory = factory(uid, coordinator, clock, notificationsAllowed))

        fun factory(
            uid: String,
            coordinator: UnderwayCoordinator,
            clock: UnderwayClock,
            notificationsAllowed: () -> Boolean,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { UnderwayViewModel(uid, coordinator, clock, notificationsAllowed) }
        }
    }
}
