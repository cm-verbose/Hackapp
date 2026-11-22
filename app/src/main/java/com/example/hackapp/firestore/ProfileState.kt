package com.example.hackapp.firestore

import com.example.hackapp.profile.Profile
import com.example.hackapp.profile.ProfileRepository
import kotlinx.coroutines.CancellationException

/**
 * What launch routing can show for an account. [UpdateRequired] is a Profile written by a newer
 * app: a Retry cannot succeed, so the UI should offer an update prompt or sign-out instead.
 * [Failed] is a storage failure where Retry makes sense.
 */
sealed interface ProfileState {
    data class Ready(val profile: Profile) : ProfileState
    /** No usable Profile: run onboarding. */
    data object Missing : ProfileState
    data class UpdateRequired(val storedVersion: Int) : ProfileState
    data class Failed(val cause: Throwable) : ProfileState
}

suspend fun ProfileRepository.loadProfileState(uid: String): ProfileState = try {
    getProfile(uid)?.let { ProfileState.Ready(it) } ?: ProfileState.Missing
} catch (e: CancellationException) {
    throw e
} catch (e: NewerSchemaException) {
    ProfileState.UpdateRequired(e.version)
} catch (e: Exception) {
    ProfileState.Failed(e)
}
