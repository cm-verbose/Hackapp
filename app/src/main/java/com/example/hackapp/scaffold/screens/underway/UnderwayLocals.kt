package com.example.hackapp.scaffold.screens.underway

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.example.hackapp.HackappApplication
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.scaffold.screens.planning.ReviewStartOutcome
import com.example.hackapp.scaffold.screens.planning.ReviewStarter
import com.example.hackapp.underway.UnderwayCoordinator
import com.example.hackapp.underway.UnderwayStartResult

/** Replaces the app's coordinator in tests, so it shares their in-memory Itineraries. */
val LocalUnderwayCoordinator = staticCompositionLocalOf<UnderwayCoordinator?> { null }

@Composable
fun currentUnderwayCoordinator(): UnderwayCoordinator =
    LocalUnderwayCoordinator.current
        ?: (LocalContext.current.applicationContext as HackappApplication).underwayCoordinator

/** Review's Start, through the coordinator. [originFor] gives the first Leg's start, or null. */
fun UnderwayCoordinator.reviewStarter(
    originFor: suspend (uid: String) -> LatLng?,
) = ReviewStarter { uid, itineraryId, demoMode, confirmEndCurrent ->
    when (val result = start(uid, itineraryId, originFor(uid), demoMode, confirmEndCurrent)) {
        is UnderwayStartResult.Started -> ReviewStartOutcome.Started(itineraryId, result.askExactAlarm)
        is UnderwayStartResult.NeedsConfirmation -> ReviewStartOutcome.NeedsConfirmation(result.current)
        is UnderwayStartResult.Blocked -> ReviewStartOutcome.Blocked
        UnderwayStartResult.NotFound -> ReviewStartOutcome.NotFound
    }
}
