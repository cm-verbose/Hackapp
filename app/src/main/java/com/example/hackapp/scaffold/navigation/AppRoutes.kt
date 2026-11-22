package com.example.hackapp.scaffold.navigation

import com.example.hackapp.domain.UserProfile
import com.example.hackapp.scaffold.screens.home.ScreenHomeDestination
import kotlinx.serialization.Serializable

/*
 * Typed routes for every page in the spec. Welcome and Home keep their existing destinations,
 * ScreenLoginDestination and ScreenHomeDestination.
 */

// Authentication
@Serializable data object RegisterRoute
@Serializable data object PasswordResetRoute

// Onboarding
@Serializable data object OnboardingVisitorTypeRoute
@Serializable data object OnboardingProfileRoute
@Serializable data object OnboardingBudgetAudienceRoute
@Serializable data object OnboardingInterestsRoute

/**
 * Where sign-in leads: onboarding for every new account, and for any account until this device's
 * profile says onboarding is done (onboarding quit halfway, or an existing account on a new device).
 */
fun afterSignInRoute(profile: UserProfile, isNewUser: Boolean): Any =
    if (isNewUser || !profile.onboarded) OnboardingVisitorTypeRoute else ScreenHomeDestination

// Settings (the top-level tabs use ScreenItineraryDestination and ScreenPassportDestination)
@Serializable data object SettingsRoute

// Planning
@Serializable data object PlanningDateTimeRoute
@Serializable data object PlanningDistrictPickRoute
@Serializable data object PlanningMealChoiceRoute
@Serializable data object PlanningStopSelectionRoute
@Serializable data object ItineraryReviewRoute

// Underway
@Serializable data object UnderwayLegRoute
@Serializable data object UnderwayArrivalRoute
@Serializable data object UnderwayAtStopRoute
@Serializable data object UnderwayEndOfDayRoute
