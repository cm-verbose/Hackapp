package com.example.hackapp.scaffold.screens.underway

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hackapp.underway.DemoController
import com.example.hackapp.underway.DemoEnableResult
import com.example.hackapp.underway.DemoStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TICK_MILLIS = 1_000L

/** State for the future demo control. It has no UI of its own. */
class DemoControlsViewModel(
    private val uid: String,
    private val controller: DemoController,
) : ViewModel() {
    private val _state = MutableStateFlow(controller.status(canAdvance = false))
    val state: StateFlow<DemoStatus> = _state.asStateFlow()

    private val _lastResult = MutableStateFlow<DemoEnableResult?>(null)
    /** Why the last [enable] was refused, if it was. */
    val lastResult: StateFlow<DemoEnableResult?> = _lastResult.asStateFlow()

    private val _exitFailed = MutableStateFlow(false)
    /** The last [exit] failed: demo mode is still on and it can be tried again. */
    val exitFailed: StateFlow<Boolean> = _exitFailed.asStateFlow()

    private val _actionFailed = MutableStateFlow(false)
    /** The last [enable] or [nextMoment] failed: show `demo_error` with Retry. Cleared on the next attempt. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()

    private var failedAction: (() -> Unit)? = null

    private var canAdvance = false

    init {
        viewModelScope.launch {
            controller.observe(uid).collect {
                canAdvance = it.canAdvance
                _state.value = it
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(TICK_MILLIS)
                _state.value = controller.status(canAdvance)
            }
        }
    }

    fun enable() = attempt(_actionFailed) { _lastResult.value = controller.enable(uid) }

    /** Exit demo mode. An unfinished run ends and moves to Past with its Stamps. */
    fun exit() = attempt(_exitFailed) { controller.exit(uid) }

    fun nextMoment() = attempt(_actionFailed) { controller.nextMoment(uid) }

    /** Repeats the action that failed last. Does nothing when none did. */
    fun retry() {
        failedAction?.invoke()
    }

    /** Runs [block]. Any new attempt clears earlier failures; a failure here sets [failedFlag]. */
    private fun attempt(failedFlag: MutableStateFlow<Boolean>, block: suspend () -> Unit) {
        failedAction = null
        _actionFailed.value = false
        _exitFailed.value = false
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failedAction = { attempt(failedFlag, block) }
                failedFlag.value = true
            }
            _state.value = controller.status(canAdvance)
        }
    }

    companion object {
        fun factory(uid: String, controller: DemoController): ViewModelProvider.Factory = viewModelFactory {
            initializer { DemoControlsViewModel(uid, controller) }
        }
    }
}
