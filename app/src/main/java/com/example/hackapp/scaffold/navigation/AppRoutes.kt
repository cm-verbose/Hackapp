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
@Serializable data class PlanningDistrictPickRoute(val itineraryId: String)
@Serializable data class PlanningMealChoiceRoute(val itineraryId: String)
/** With [districtId] set, Selection opens on the gap after [afterStopId] (null = before the first Stop). */
@Serializable data class PlanningStopSelectionRoute(
    val itineraryId: String,
    val afterStopId: String? = null,
    val districtId: String? = null,
    /** Shows the colour transition that opens selection. Set only when arriving from Meal choice. */
    val intro: Boolean = false,
)
@Serializable data class ItineraryReviewRoute(val itineraryId: String)

// Sharing
/** Flick an Itinerary to a nearby phone. A null [itineraryId] only receives. */
@Serializable data class ShareItineraryRoute(val itineraryId: String? = null)

/** Flick a Stamp to a friend, or receive one with [stampKey] null. */
@Serializable data class ShareStampRoute(val stampKey: String? = null)

// Underway
/** One route for Leg, Arrival, At the Stop and End of day: the page follows the Underway phase. */
/** Underway. [started] plays the start panel once, right after Start in Review. */
@Serializable data class UnderwayLegRoute(val started: Boolean = false)
