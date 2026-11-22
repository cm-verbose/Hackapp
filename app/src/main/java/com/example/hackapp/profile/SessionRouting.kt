package com.example.hackapp.profile

import com.example.hackapp.auth.AuthService

enum class SessionDestination { Welcome, Onboarding, Home }

/** Having a saved Profile decides the route, because older accounts have none yet. */
suspend fun resolveSessionDestination(
    auth: AuthService,
    profiles: ProfileRepository,
): SessionDestination {
    val user = auth.currentUser ?: return SessionDestination.Welcome
    return if (profiles.getProfile(user.uid) == null) {
        SessionDestination.Onboarding
    } else {
        SessionDestination.Home
    }
}
