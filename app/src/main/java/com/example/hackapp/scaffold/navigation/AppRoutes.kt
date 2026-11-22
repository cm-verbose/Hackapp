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

// Read-only Itinerary pages, opened from the Itineraries tab
@Serializable data class PastItineraryRoute(val itineraryId: String)
@Serializable data class ArchivedItineraryRoute(val itineraryId: String)

// Planning
/** A null [itineraryId] starts a new draft. */
@Serializable data class PlanningDateTimeRoute(val itineraryId: String? = null)
@Serializable data class PlanningBudgetRoute(val itineraryId: String)
@Serializable data class PlanningBudgetEditRoute(val itineraryId: String)
@Serializable data class PlanningDistrictPickRoute(val itineraryId: String)
@Serializable data class PlanningMealChoiceRoute(val itineraryId: String)
@Serializable data class PlanningStopSelectionRoute(val itineraryId: String)
@Serializable data class ItineraryReviewRoute(val itineraryId: String)

// Underway
@Serializable data object UnderwayLegRoute
@Serializable data object UnderwayArrivalRoute
@Serializable data object UnderwayAtStopRoute
@Serializable data object UnderwayEndOfDayRoute
