package com.example.hackapp.itinerary

import com.example.hackapp.data.Cost
import com.example.hackapp.data.CostStatus
import com.example.hackapp.profile.BudgetBracket
import com.example.hackapp.profile.Interest
import com.example.hackapp.services.LegEstimate
import com.example.hackapp.services.LegEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EventSelectionTest {
    private val legs = fixedLegs(20)
    private val nextDay = Nov28.plusDays(1)

    private fun ids(itinerary: Itinerary, vararg events: com.example.hackapp.data.Event) =
        reachableShowtimes(EventSearch(itinerary, events.toList(), context(), HERE), legs).map { it.event.id }

    private val previous = stop("p", at(Nov28, 18), at(Nov28, 19))

    @Test
    fun `reachable boundary is departure plus the transit total`() {
        val itinerary = night(stops = listOf(previous))
        assertEquals(
            listOf("exact"),
            ids(
                itinerary,
                event("exact", start = at(Nov28, 19, 20).toString()),
                event("early", start = at(Nov28, 19, 19).toString()),
            ),
        )
    }

    @Test
    fun `the estimator is asked for walking inside a District and transit across Districts`() {
        fun modeFor(district: String): com.example.hackapp.services.LegMode? {
            var mode: com.example.hackapp.services.LegMode? = null
            val spy = LegEstimator { _, _, m ->
                mode = m
                LegEstimate(1.0, 1, 1)
            }
            reachableShowtimes(
                EventSearch(
                    night(stops = listOf(previous)),
                    listOf(event("a", start = at(Nov28, 20).toString(), district = district)),
                    context(),
                    district,
                ),
                spy,
            )
            return mode
        }
        assertEquals(com.example.hackapp.services.LegMode.WALK, modeFor(HERE))
        assertEquals(com.example.hackapp.services.LegMode.TRANSIT, modeFor(NEXT))
    }

    @Test
    fun `window start is inclusive and window end exclusive`() {
        assertEquals(
            listOf("first", "last"),
            ids(
                night(),
                event("before", start = at(Nov28, 17, 59).toString()),
                event("first", start = at(Nov28, 18).toString()),
                event("last", start = at(nextDay, 1, 59).toString()),
                event("end", start = at(nextDay, 2).toString()),
            ),
        )
    }

    @Test
    fun `an Event already in progress cannot be added`() {
        val running = event("a", start = at(Nov28, 18).toString(), end = at(Nov28, 21).toString())
        val during = context(now = instant(Nov28, 19))
        assertTrue(reachableShowtimes(EventSearch(night(), listOf(running), during, HERE), legs).isEmpty())
    }

    @Test
    fun `after-midnight Events belong to the Itinerary date`() {
        val found = ids(night(), event("late", start = at(nextDay, 0, 30).toString()))
        assertEquals(listOf("late"), found)
        val wrongNight = ids(night(date = nextDay), event("late", start = at(nextDay, 0, 30).toString()))
        assertTrue(wrongNight.isEmpty())
    }

    @Test
    fun `Events touching a Meal Block are kept, overlapping ones hidden`() {
        val dinner = MealBlock("d", MealKind.Dinner, LocalTime.of(20, 0))
        val found = ids(
            night(blocks = listOf(dinner)),
            event("ends-at-block", start = at(Nov28, 18).toString(), end = at(Nov28, 20).toString()),
            event("ends-in-block", start = at(Nov28, 18).toString(), end = at(Nov28, 20, 1).toString()),
            event("starts-in-block", start = at(Nov28, 21, 29).toString()),
            event("starts-at-block-end", start = at(Nov28, 21, 30).toString()),
        )
        assertEquals(listOf("ends-at-block", "starts-at-block-end"), found)
    }

    @Test
    fun `missing end uses the typical stay for Meal Block overlap`() {
        val dinner = MealBlock("d", MealKind.Dinner, LocalTime.of(20, 0))
        val found = ids(
            night(blocks = listOf(dinner)),
            event("music", start = at(Nov28, 18, 30).toString(), type = "music"),
            event("exhibition", start = at(Nov28, 19).toString(), type = "exhibition"),
        )
        assertEquals(listOf("exhibition"), found)
    }

    @Test
    fun `skipping a Meal frees its block`() {
        val skipped = MealBlock("d", MealKind.Dinner, LocalTime.of(20, 0), skipped = true)
        assertEquals(listOf("a"), ids(night(blocks = listOf(skipped)), event("a", start = at(Nov28, 20, 30).toString())))
    }

    @Test
    fun `declining Meal planning frees every block`() {
        val block = MealBlock("d", MealKind.Dinner, LocalTime.of(20, 0))
        val itinerary = night(blocks = listOf(block)).copy(planMeals = false)
        assertEquals(listOf("a"), ids(itinerary, event("a", start = at(Nov28, 20, 30).toString())))
    }

    @Test
    fun `an Event in the Itinerary hides all of its showtimes`() {
        val first = event("s1", title = "Film", venue = "Cinema", start = at(Nov28, 18).toString())
        val second = event("s2", title = "Film", venue = "Cinema", start = at(Nov28, 22).toString())
        val inPlan = stop("x", at(Nov28, 18), at(Nov28, 20), sourceId = "s1")
        assertTrue(ids(night(stops = listOf(inPlan)), first, second).isEmpty())
    }

    @Test
    fun `a filled gap requires reaching the next Stop in time`() {
        val after = stop("n", at(Nov28, 22), at(Nov28, 23))
        val itinerary = night(stops = listOf(after))
        val found = reachableShowtimes(
            EventSearch(
                itinerary,
                listOf(
                    event("fits", start = at(Nov28, 19).toString(), end = at(Nov28, 21, 40).toString()),
                    event("late", start = at(Nov28, 19).toString(), end = at(Nov28, 21, 41).toString()),
                ),
                context(),
                HERE,
                gap = itinerary.gapAfter(null),
            ),
            legs,
        ).map { it.event.id }
        assertEquals(listOf("fits"), found)
    }

    @Test
    fun `other Districts and Events without coordinates are excluded`() {
        assertTrue(ids(night(), event("a", district = NEXT), event("b", latitude = null)).isEmpty())
    }

    @Test
    fun `repeated showtimes on one day become one card`() {
        val events = listOf(
            event("late", title = "Le Film", venue = "Cinéma", start = at(Nov28, 21).toString(), type = "cinema"),
            event("early", title = "  le  film ", venue = "cinema", start = at(Nov28, 19).toString(), type = "cinema"),
            event("other-venue", title = "Le Film", venue = "Autre", start = at(Nov28, 19).toString(), type = "cinema"),
        )
        val cards = rankedEventCards(EventSearch(night(), events, context(), HERE), legs)
        assertEquals(2, cards.size)
        val merged = cards.single { it.showtimes.size == 2 }
        assertEquals(listOf("early", "late"), merged.showtimes.map { it.event.id })
    }

    @Test
    fun `showtimes on different days stay separate`() {
        val first = event("a", title = "Film", venue = "Cinema", start = at(Nov28, 23).toString())
        val second = event("b", title = "Film", venue = "Cinema", start = at(nextDay, 0, 30).toString())
        assertFalse(first.showtimeKey() == second.showtimeKey())
    }

    @Test
    fun `key ignores the device zone`() {
        val local = event("a", start = "2026-11-28T23:30:00-05:00")
        val utc = event("b", start = "2026-11-29T04:30:00Z", title = local.title, venue = local.location.venueName)
        assertEquals(local.showtimeKey(), utc.showtimeKey())
    }

    @Test
    fun `groups by hour, then type with other last`() {
        val events = listOf(
            event("o", type = "other", start = at(Nov28, 19, 5).toString()),
            event("m", type = "music", start = at(Nov28, 19, 59).toString()),
            event("c", type = "comedy", start = at(Nov28, 20).toString()),
        )
        val groups = groupEventCards(rankedEventCards(EventSearch(night(), events, context(), HERE), legs))
        assertEquals(listOf(19, 20), groups.map { it.hourStart.hour })
        assertEquals("other", groups[0].types.last().type)
        assertEquals(setOf("other", "music"), groups[0].types.map { it.type }.toSet())
    }

    @Test
    fun `ranking is Interests, known in budget, unknown, then travel`() {
        val near = com.example.hackapp.data.model.LatLng(45.5, -73.6)
        val estimator = LegEstimator { _, to, _ ->
            val minutes = if (to == near) 5 else 30
            LegEstimate(1.0, minutes, 10)
        }
        val free = Cost(CostStatus.FREE)
        val pricey = Cost(CostStatus.PAID, maxPrice = 80.0)
        val events = listOf(
            event("over", cost = pricey, type = "comedy"),
            event("unknown-near", type = "comedy"),
            event("unknown-far", type = "comedy", latitude = 45.6),
            event("free-far", cost = free, type = "comedy", latitude = 45.6),
            event("interest-unknown", type = "music", latitude = 45.6),
        )
        val ctx = context().copy(interests = setOf(Interest.Music), defaultEventBudget = BudgetBracket.Under20)
        val cards = rankedEventCards(EventSearch(night(), events, ctx, HERE, origin = near), estimator)
        assertEquals(
            listOf("interest-unknown", "free-far", "unknown-near", "unknown-far", "over"),
            cards.map { it.showtimes.first().event.id },
        )
    }

    @Test
    fun `median price counts as a known price`() {
        val paid = event("a", cost = Cost(CostStatus.PAID))
        val search = EventSearch(
            night(),
            listOf(paid),
            context().copy(defaultEventBudget = BudgetBracket.Under20),
            HERE,
            medianPrices = mapOf(paid.showtimeKey() to 15.0),
        )
        val card = rankedEventCards(search, legs).single()
        assertEquals(PriceRank.KnownWithinBudget, card.priceRank)
        assertEquals(15.0, card.priceCad!!, 0.0)
    }

    @Test
    fun `gaps work across a daylight saving change`() {
        val springForward = LocalDate.of(2026, 3, 7)
        val after = springForward.plusDays(1)
        val itinerary = night(date = springForward, end = LocalTime.of(4, 0))
        // Clocks jump from 02:00 to 03:00, so this window lasts nine hours.
        assertEquals(
            9 * 3600L,
            java.time.Duration.between(
                itinerary.planningWindow(Toronto).start,
                itinerary.planningWindow(Toronto).end,
            ).seconds,
        )
        val ctx = context(now = instant(springForward, 8))
        val previousStop = stop("p", at(after, 1, 50), at(after, 1, 55))
        val withStop = itinerary.copy(stops = listOf(previousStop))
        // 01:55 EST plus 20 minutes is 03:15 EDT.
        val found = reachableShowtimes(
            EventSearch(
                withStop,
                listOf(
                    event("ok", start = "2026-03-08T03:15:00-04:00"),
                    event("early", start = "2026-03-08T03:14:00-04:00"),
                ),
                ctx,
                HERE,
            ),
            legs,
        ).map { it.event.id }
        assertEquals(listOf("ok"), found)
    }
}
