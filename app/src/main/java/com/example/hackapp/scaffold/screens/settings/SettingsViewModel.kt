package com.example.hackapp.scaffold.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hackapp.data.model.CuisineGroup
import com.example.hackapp.profile.Profile
import com.example.hackapp.profile.ProfileRepository
import com.example.hackapp.scaffold.screens.onboarding.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The saved Profile with the display names it refers to. */
data class SettingsProfile(
    val profile: Profile,
    val districtName: String?,
    val cuisines: List<CuisineGroup>,
)

/** [profile] holds null when this account has no saved Profile. */
data class SettingsUiState(val profile: LoadState<SettingsProfile?> = LoadState.Loading)

class SettingsViewModel(
    private val uid: String?,
    private val profiles: ProfileRepository,
    private val loadDistrictNames: suspend () -> Map<String, String>,
    private val loadCuisines: suspend () -> List<CuisineGroup>,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = SettingsUiState()
        viewModelScope.launch {
            _state.value = SettingsUiState(
                try {
                    LoadState.Ready(uid?.let { profiles.getProfile(it) }?.let { describe(it) })
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    LoadState.Failed
                },
            )
        }
    }

    private suspend fun describe(profile: Profile): SettingsProfile {
        // Cuisine names only decorate a summary line, so a read failure leaves them out.
        val cuisines = try {
            if (profile.cuisineGroups.isEmpty()) emptyList() else loadCuisines()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        return SettingsProfile(
            profile = profile,
            districtName = loadDistrictNames()[profile.districtOfStayId],
            cuisines = cuisines.filter { it.code in profile.cuisineGroups },
        )
    }

    companion object {
        fun factory(
            uid: String?,
            profiles: ProfileRepository,
            loadDistrictNames: suspend () -> Map<String, String>,
            loadCuisines: suspend () -> List<CuisineGroup>,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(uid, profiles, loadDistrictNames, loadCuisines) }
        }
    }
}
