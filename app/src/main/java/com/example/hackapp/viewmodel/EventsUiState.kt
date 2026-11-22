package com.example.hackapp.viewmodel

import com.example.hackapp.data.Event

sealed interface EventsUiState {
    data object Idle : EventsUiState
    data object Loading : EventsUiState
    data class Success(val events: List<Event>) : EventsUiState
    data class Error(val message: String) : EventsUiState
}
