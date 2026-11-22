package com.example.hackapp.scaffold.screens.share

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.activeStops
import com.example.hackapp.scaffold.sandbox.SandboxItem
import com.example.hackapp.scaffold.screens.itinerary.itineraryTitle
import com.example.hackapp.ui.theme.HackappCorners
import java.util.UUID

/**
 * What a share screen caught from nearby phones: each thrown Stamp or Itinerary lands on screen as
 * it was thrown, and waits there until the person opens and saves it.
 */
@Stable
internal class Catches<T> {
    /** Caught objects by their key in the physics world, with the sender's readable name. */
    val byKey = mutableStateMapOf<Any, Pair<T, String?>>()

    /** Keys of saved objects, which leave the world. */
    var removed by mutableStateOf(emptySet<Any>())
        private set

    /** Records a caught object and returns the key for its [SandboxItem]. */
    fun add(value: T, fromName: String?): String {
        val key = "caught-${UUID.randomUUID()}"
        byKey[key] = value to fromName
        return key
    }

    fun saved(key: Any) {
        byKey.remove(key)
        removed = removed + key
    }
}

/** The Itinerary card thrown between phones: its title and number of Stops, on the Hero colour. */
internal fun itineraryCardItem(key: Any, itinerary: Itinerary, districtNames: Map<String, String>, color: Color) =
    SandboxItem(
        key = key,
        color = color,
        shape = RoundedCornerShape(HackappCorners.Large),
        width = 220.dp,
        height = 140.dp,
        restitution = 0.3f,
    ) {
        val stops = itinerary.activeStops.size
        ShareToken(
            title = itineraryTitle(itinerary.date, itinerary.districtIds.firstOrNull()?.let(districtNames::get)),
            stops = pluralStringResource(R.plurals.stops_count, stops, stops),
        )
    }
