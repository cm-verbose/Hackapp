package com.example.hackapp.firestore

import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.ItineraryRepository
import com.example.hackapp.itinerary.ShowtimeKey
import com.example.hackapp.itinerary.TicketPriceRepository
import com.example.hackapp.itinerary.TicketPriceSummary
import com.example.hackapp.itinerary.isValidTicketPrice
import com.example.hackapp.itinerary.preconditionHolds
import com.example.hackapp.profile.Profile
import com.example.hackapp.profile.ProfileRepository
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Failure mapping. The interfaces expect: unreadable or foreign data counts as missing (null, or
 * skipped in a list), and a storage failure throws (callers catch Exception and keep going). So
 * Firestore errors, including PERMISSION_DENIED and UNAVAILABLE, propagate from suspend calls and
 * close the observing flows with the same exception. The one exception is a write still pending after
 * [WRITE_TIMEOUT_MS] (offline): it counts as accepted, and a later server rejection is only logged
 * by [awaitWrite], because no caller is waiting any more. See [onLateWriteFailure].
 */

private fun DocumentSnapshot.dataOrNull(): Map<String, Any?>? = if (exists()) data else null

class FirestoreProfileRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val clock: () -> Long = System::currentTimeMillis,
) : ProfileRepository {
    private fun ref(uid: String) = db.collection(FirestoreDocuments.USERS).document(uid)
        .collection(FirestoreDocuments.PROFILE).document(FirestoreDocuments.PROFILE_DOC)

    override suspend fun getProfile(uid: String): Profile? =
        FirestoreDocuments.profileFrom(uid, ref(uid).get().await().dataOrNull())

    override suspend fun saveProfile(uid: String, profile: Profile) {
        awaitWrite(ref(uid).set(FirestoreDocuments.profileDoc(uid, profile, clock())))
    }
}

class FirestoreItineraryRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
) : ItineraryRepository {
    private fun collection(uid: String) = db.collection(FirestoreDocuments.USERS).document(uid)
        .collection(FirestoreDocuments.ITINERARIES)

    override fun observeItineraries(uid: String): Flow<List<Itinerary>> = callbackFlow {
        val registration = collection(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(
                    snapshot.documents
                        .mapNotNull { FirestoreDocuments.itineraryFrom(uid, it.id, it.data, strict = false) }
                        .sortedByDescending { it.updatedAtMillis },
                )
            }
        }
        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    override suspend fun listItineraries(uid: String, freshOnly: Boolean): List<Itinerary> {
        val source = if (freshOnly) Source.SERVER else Source.DEFAULT
        return collection(uid).get(source).await().documents
            .mapNotNull { FirestoreDocuments.itineraryFrom(uid, it.id, it.data, strict = false) }
            .sortedByDescending { it.updatedAtMillis }
    }

    override suspend fun getItinerary(uid: String, id: String): Itinerary? =
        FirestoreDocuments.itineraryFrom(uid, id, collection(uid).document(id).get().await().dataOrNull(), strict = true)

    override suspend fun saveItinerary(itinerary: Itinerary) {
        collection(itinerary.ownerUid).document(itinerary.id)
            .set(FirestoreDocuments.itineraryDoc(itinerary)).let { awaitWrite(it) }
    }

    override suspend fun saveItineraryIfUnchanged(itinerary: Itinerary, expectedUpdatedAtMillis: Long?): Boolean {
        val ref = collection(itinerary.ownerUid).document(itinerary.id)
        return db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            val stored = FirestoreDocuments.storedUpdatedAt(snapshot.dataOrNull())
            if (!preconditionHolds(stored, expectedUpdatedAtMillis)) {
                false
            } else {
                transaction.set(ref, FirestoreDocuments.itineraryDoc(itinerary))
                true
            }
        }.await()
    }

    override suspend fun deleteItinerary(uid: String, id: String) {
        awaitWrite(collection(uid).document(id).delete())
    }
}

/**
 * Own reports are read and written directly; the median is read from `ticketSummaries/{keyId}`.
 * A client never reads other people's reports. That aggregate must be written by a trusted
 * Cloud Function (design in the team report); until it is deployed, summaries are always null.
 */
class FirestoreTicketPriceRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
) : TicketPriceRepository {
    private fun summaryRef(key: ShowtimeKey): DocumentReference =
        db.collection(FirestoreDocuments.TICKET_SUMMARIES).document(FirestoreDocuments.keyId(key))

    private fun reportRef(uid: String, key: ShowtimeKey): DocumentReference =
        db.collection(FirestoreDocuments.TICKET_REPORTS).document(FirestoreDocuments.keyId(key))
            .collection(FirestoreDocuments.REPORTS).document(uid)

    override fun observeSummary(key: ShowtimeKey): Flow<TicketPriceSummary?> = callbackFlow {
        val registration = summaryRef(key).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(FirestoreDocuments.summaryFrom(snapshot.dataOrNull()))
            }
        }
        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    override suspend fun getSummary(key: ShowtimeKey): TicketPriceSummary? =
        FirestoreDocuments.summaryFrom(summaryRef(key).get().await().dataOrNull())

    override suspend fun getOwnReport(uid: String, key: ShowtimeKey): Double? =
        FirestoreDocuments.reportFrom(uid, reportRef(uid, key).get().await().dataOrNull())

    override suspend fun saveReport(uid: String, key: ShowtimeKey, priceCad: Double): Boolean {
        if (!isValidTicketPrice(priceCad)) return false
        awaitWrite(reportRef(uid, key).set(FirestoreDocuments.reportDoc(uid, priceCad, FieldValue.serverTimestamp())))
        return true
    }

    override suspend fun deleteReport(uid: String, key: ShowtimeKey) {
        awaitWrite(reportRef(uid, key).delete())
    }
}

/**
 * Firestore write tasks only complete once the server acknowledges. Offline that never happens,
 * although the SDK has queued the write and applies it locally. So a write still pending after
 * [WRITE_TIMEOUT_MS] counts as accepted, while a failure within that time (rules, bad data) throws.
 * A failure arriving after the timeout can no longer be thrown, so it goes to [onLateWriteFailure]:
 * a server rejection then leaves the local view ahead of the server until the SDK reverts it.
 */
internal const val WRITE_TIMEOUT_MS = 2_000L

/** Receives a write failure that arrives after the timeout. Defaults to a log line; replaceable. */
internal var onLateWriteFailure: (Throwable) -> Unit = { Log.w("FirestoreWrite", "Write rejected after timeout", it) }

private suspend fun awaitWrite(task: Task<Void>) {
    var finished = false
    withTimeoutOrNull(WRITE_TIMEOUT_MS) { task.await(); finished = true }
    // Timed out: the write is queued. Keep listening so a later rejection is not lost.
    if (!finished) task.addOnFailureListener { onLateWriteFailure(it) }
}
