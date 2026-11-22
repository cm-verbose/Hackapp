package com.example.hackapp.firestore

import android.content.Context
import com.example.hackapp.itinerary.InMemoryTicketPriceRepository
import com.example.hackapp.itinerary.ItineraryRepository
import com.example.hackapp.itinerary.TicketPriceRepository
import com.example.hackapp.itinerary.createItineraryRepository
import com.example.hackapp.profile.ProfileRepository
import com.example.hackapp.profile.createProfileRepository

/**
 * SELECTION POINT. Off by default: the app keeps its local DataStore repositories.
 * Switching to Firestore is the product owner's decision (see the switch-on steps in the team
 * report): deploy the rules and the aggregate function first, then set this to true.
 * Local data is not migrated.
 */
const val USE_FIRESTORE_REPOSITORIES = false

object RepositorySelection {
    fun profile(context: Context): ProfileRepository =
        if (USE_FIRESTORE_REPOSITORIES) FirestoreProfileRepository() else createProfileRepository(context)

    fun itineraries(context: Context): ItineraryRepository =
        if (USE_FIRESTORE_REPOSITORIES) FirestoreItineraryRepository() else createItineraryRepository(context)

    fun ticketPrices(): TicketPriceRepository =
        if (USE_FIRESTORE_REPOSITORIES) FirestoreTicketPriceRepository() else InMemoryTicketPriceRepository()
}
