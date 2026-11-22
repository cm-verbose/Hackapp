package com.example.hackapp.underway

import com.example.hackapp.data.model.LatLng
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.Nov28
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.itinerary.StopKind
import com.example.hackapp.itinerary.Toronto
import com.example.hackapp.itinerary.at
import com.example.hackapp.itinerary.fixedLegs
import com.example.hackapp.itinerary.night
import com.example.hackapp.itinerary.stop
import java.time.Instant
import java.time.ZoneId

class FakeClock(var current: Instant, override val zone: ZoneId = Toronto) : UnderwayClock {
    override fun now(): Instant = current
}

val Nov29 = Nov28.plusDays(1)

/** A: Event 19:00-21:00. B: Meal 21:30-23:00. C: Event 00:30-01:30 the next day. Legs take 20 min. */
fun stopA() = stop("a", at(Nov28, 19), at(Nov28, 21))
fun stopB() = stop("b", at(Nov28, 21, 30), at(Nov28, 23), kind = StopKind.Meal)
fun stopC() = stop("c", at(Nov29, 0, 30), at(Nov29, 1, 30))

fun threeStops(): Itinerary = night(stops = listOf(stopA(), stopB(), stopC()))

fun ctxOf(itinerary: Itinerary = threeStops()) = UnderwayContext(itinerary, fixedLegs(20), Toronto)

fun t(hour: Int, minute: Int = 0, day: java.time.LocalDate = Nov28): Instant = at(day, hour, minute).toInstant()

fun begin(itinerary: Itinerary = threeStops(), now: Instant = t(18), origin: LatLng? = null) =
    UnderwayEngine.begin(itinerary, now, origin)!!

/** Applies [events] in order and returns the last state. Fails if one is rejected. */
fun UnderwayContext.run(state: UnderwayState, vararg steps: Pair<Instant, UnderwayEvent>): UnderwayState {
    var s = state
    for ((time, event) in steps) {
        val tr = UnderwayEngine.reduce(this, s, event, time)
        check(tr.rejection == null) { "$event rejected: ${tr.rejection}" }
        s = tr.state
    }
    return s
}

fun Stop.named(name: String) = copy(name = name)
