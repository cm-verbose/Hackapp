package com.example.hackapp.scaffold.screens.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hackapp.itinerary.ItineraryRepository
import com.example.hackapp.scaffold.screens.onboarding.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ItinerariesContent(
    val planning: List<ItineraryCardUi>,
    val past: List<ItineraryCardUi>,
    val archived: List<ItineraryCardUi>,
) {
    val isEmpty: Boolean get() = planning.isEmpty() && past.isEmpty() && archived.isEmpty()
}

data class ItinerariesUiState(
    val content: LoadState<ItinerariesContent> = LoadState.Loading,
    val pendingDeleteId: String? = null,
    val deleteFailed: Boolean = false,
)

private data class DeleteState(val pendingId: String? = null, val failed: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class ItinerariesViewModel(
    private val uid: String?,
    private val itineraries: ItineraryRepository,
    private val loadDistrictNames: suspend () -> Map<String, String>,
) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val delete = MutableStateFlow(DeleteState())
    private val districtNames = MutableStateFlow<Map<String, String>>(emptyMap())

    private val content = retry.flatMapLatest {
        if (uid == null) {
            flowOf<LoadState<List<com.example.hackapp.itinerary.Itinerary>>>(LoadState.Ready(emptyList()))
        } else {
            itineraries.observeItineraries(uid)
                .map<_, LoadState<List<com.example.hackapp.itinerary.Itinerary>>> { LoadState.Ready(it) }
                .onStart { emit(LoadState.Loading) }
                .catch { emit(LoadState.Failed) }
        }
    }

    val state: StateFlow<ItinerariesUiState> = combine(content, districtNames, delete) { load, names, deleting ->
        ItinerariesUiState(
            content = when (load) {
                LoadState.Loading -> LoadState.Loading
                LoadState.Failed -> LoadState.Failed
                is LoadState.Ready -> {
                    val sections = load.value.toSections()
                    LoadState.Ready(
                        ItinerariesContent(
                            planning = sections.planning.map { it.toCard(names) },
                            past = sections.past.map { it.toCard(names) },
                            archived = sections.archived.map { it.toCard(names) },
                        ),
                    )
                }
            },
            pendingDeleteId = deleting.pendingId,
            deleteFailed = deleting.failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ItinerariesUiState())

    init {
        viewModelScope.launch { districtNames.value = loadDistrictNames() }
    }

    fun retry() = retry.update { it + 1 }

    fun requestDelete(id: String) = delete.update { DeleteState(pendingId = id) }

    fun dismissDelete() = delete.update { DeleteState() }

    fun confirmDelete() {
        val id = delete.value.pendingId ?: return
        val owner = uid ?: return
        delete.update { DeleteState() }
        viewModelScope.launch {
            try {
                // Read again: the phase may have changed since the menu was opened.
                val current = itineraries.getItinerary(owner, id)
                if (current?.canDelete == true) itineraries.deleteItinerary(owner, id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                delete.update { it.copy(failed = true) }
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(
            uid: String?,
            itineraries: ItineraryRepository,
            loadDistrictNames: suspend () -> Map<String, String>,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ItinerariesViewModel(uid, itineraries, loadDistrictNames) }
        }
    }
}
