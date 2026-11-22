package com.example.hackapp.scaffold.navigation

import kotlinx.serialization.Serializable

/*
 * Typed routes for every page in the spec. Welcome and Home keep their existing destinations,
 * ScreenLoginDestination and ScreenHomeDestination.
 */

// Authentication
@Serializable data object RegisterRoute
@Serializable data object PasswordResetRoute

/** Start destination: decides between Welcome, onboarding and Home. */
@Serializable data object SessionGateRoute

// Onboarding
@Serializable data object OnboardingGraphRoute
@Serializable data object OnboardingVisitorTypeRoute
@Serializable data object OnboardingProfileRoute
@Serializable data object OnboardingBudgetAudienceRoute
@Serializable data object OnboardingInterestsRoute

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
