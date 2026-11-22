package com.example.hackapp.scaffold.navigation

import com.example.hackapp.domain.UserProfile
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination
import org.junit.Assert.assertEquals
import org.junit.Test

/** T4: a new account always onboards; an existing one onboards until this device's profile is done. */
class AfterSignInRouteTest {
    private val done = UserProfile(onboarded = true)
    private val notDone = UserProfile(onboarded = false)

    @Test fun aNewAccountOnboardsEvenIfThisDeviceWasOnboarded() {
        assertEquals(OnboardingVisitorTypeRoute, afterSignInRoute(done, isNewUser = true))
    }

    @Test fun aNewAccountOnboards() {
        assertEquals(OnboardingVisitorTypeRoute, afterSignInRoute(notDone, isNewUser = true))
    }

    @Test fun anUnfinishedOnboardingResumesAfterSignIn() {
        assertEquals(OnboardingVisitorTypeRoute, afterSignInRoute(notDone, isNewUser = false))
    }

    @Test fun aReturningOnboardedAccountGoesHome() {
        assertEquals(ScreenHomeDestination, afterSignInRoute(done, isNewUser = false))
    }
}
