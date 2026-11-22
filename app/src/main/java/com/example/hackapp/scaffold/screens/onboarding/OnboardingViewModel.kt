package com.example.hackapp.scaffold.screens.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hackapp.auth.AuthService
import com.example.hackapp.data.model.CuisineGroup
import com.example.hackapp.profile.AgeRange
import com.example.hackapp.profile.AudiencePreference
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.Interest
import com.example.hackapp.profile.ProfileRepository
import com.example.hackapp.profile.normalizedAudience
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data object Failed : LoadState<Nothing>
}

data class OnboardingUiState(
    val draft: OnboardingDraft = OnboardingDraft(),
    val districts: LoadState<List<DistrictOption>> = LoadState.Loading,
    val cuisines: LoadState<List<CuisineGroup>> = LoadState.Loading,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val finished: Boolean = false,
)

/** Shared by the onboarding routes through their navigation graph's back stack entry. */
class OnboardingViewModel(
    private val savedState: SavedStateHandle,
    private val auth: AuthService,
    private val profiles: ProfileRepository,
    private val loadDistricts: suspend () -> List<DistrictOption>,
    private val loadCuisines: suspend () -> List<CuisineGroup>,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState(draft = restoreDraft()))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        retryDistricts()
        retryCuisines()
    }

    fun setName(value: String) = updateDraft { copy(name = value) }
    fun setEmail(value: String) = updateDraft { copy(email = value) }
    fun setAgeRange(value: AgeRange?) = updateDraft { copy(ageRange = value) }
    fun setDistrict(id: String) = updateDraft { copy(districtOfStayId = id) }
    fun setLunchBudget(value: BudgetBracket?) = updateDraft { copy(lunchBudget = value) }
    fun setDinnerBudget(value: BudgetBracket?) = updateDraft { copy(dinnerBudget = value) }
    fun setEventBudget(value: BudgetBracket?) = updateDraft { copy(eventBudget = value) }
    fun setLateNightEnabled(value: Boolean) = updateDraft { copy(lateNightEnabled = value) }
    fun setLateNightBudget(value: BudgetBracket?) = updateDraft { copy(lateNightBudget = value) }
    fun setInterests(value: Set<Interest>) = updateDraft { copy(interests = value) }
    fun setCuisineGroups(value: Set<String>) = updateDraft { copy(cuisineGroups = value) }

    fun setAudience(requested: Set<AudiencePreference>) =
        updateDraft { copy(audience = normalizedAudience(audience, requested)) }

    fun retryDistricts() = load(
        start = { copy(districts = LoadState.Loading) },
        fetch = loadDistricts,
        done = { copy(districts = it) },
    )

    fun retryCuisines() = load(
        start = { copy(cuisines = LoadState.Loading) },
        fetch = loadCuisines,
        done = { copy(cuisines = it) },
    )

    /** Saves the Profile. With [skipPreferences], no Interests or cuisines are kept. */
    fun finish(skipPreferences: Boolean) {
        val current = _state.value
        if (current.saving || current.finished) return
        val uid = auth.currentUser?.uid
        val profile = current.draft.toProfile()?.let { complete ->
            if (skipPreferences) {
                complete.copy(interests = emptySet(), cuisineGroups = emptySet())
            } else {
                complete.copy(cuisineGroups = allowedCuisines(current))
            }
        }
        if (uid == null || profile == null) {
            _state.update { it.copy(saveFailed = true) }
            return
        }
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                profiles.saveProfile(uid, profile)
                _state.update { it.copy(saving = false, finished = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    /** Drops a bar choice made before the audience changed to one that hides bars. */
    private fun allowedCuisines(state: OnboardingUiState): Set<String> {
        val groups = (state.cuisines as? LoadState.Ready)?.value ?: return state.draft.cuisineGroups
        val visible = visibleCuisineGroups(groups, state.draft.audience).map { it.code }.toSet()
        return state.draft.cuisineGroups intersect visible
    }

    private fun <T> load(
        start: OnboardingUiState.() -> OnboardingUiState,
        fetch: suspend () -> T,
        done: OnboardingUiState.(LoadState<T>) -> OnboardingUiState,
    ) {
        _state.update { it.start() }
        viewModelScope.launch {
            val result = try {
                LoadState.Ready(fetch())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                LoadState.Failed
            }
            _state.update { it.done(result) }
        }
    }

    private fun updateDraft(change: OnboardingDraft.() -> OnboardingDraft) {
        _state.update { it.copy(draft = it.draft.change(), saveFailed = false) }
        savedState[DRAFT_KEY] = DraftJson.encodeToString(OnboardingDraft.serializer(), _state.value.draft)
    }

    private fun restoreDraft(): OnboardingDraft {
        val saved = savedState.get<String>(DRAFT_KEY)?.let {
            runCatching { DraftJson.decodeFromString(OnboardingDraft.serializer(), it) }.getOrNull()
        }
        if (saved != null) return saved
        val user = auth.currentUser
        return OnboardingDraft(name = user?.name.orEmpty(), email = user?.email.orEmpty())
    }

    companion object {
        private const val DRAFT_KEY = "onboarding_draft"
        private val DraftJson = Json { ignoreUnknownKeys = true }

        fun factory(
            auth: AuthService,
            profiles: ProfileRepository,
            loadDistricts: suspend () -> List<DistrictOption>,
            loadCuisines: suspend () -> List<CuisineGroup>,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                OnboardingViewModel(createSavedStateHandle(), auth, profiles, loadDistricts, loadCuisines)
            }
        }
    }
}
