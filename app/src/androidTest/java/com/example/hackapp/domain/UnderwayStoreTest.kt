package com.example.hackapp.domain

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hackapp.services.UnderwayNotifier
import com.example.hackapp.ui.theme.StopType
import java.io.FileInputStream
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T13a, instrumented: behaviour that needs the real DataStore and AlarmManager.
 * WARNING: uses the app's own "trip" DataStore and alarms, so it wipes any trip in progress on the
 * device it runs on. Run on the emulator only.
 */
@RunWith(AndroidJUnit4::class)
class UnderwayStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = UnderwayStore(context)
    private val zone = ZoneId.of("America/Toronto")
    private val notifier = UnderwayNotifier(context, zone)

    private fun trip(stops: Int, firstStartFromNow: Long = 120): Itinerary {
        val base = LocalDateTime.now(zone).plusMinutes(firstStartFromNow).withSecond(0).withNano(0)
        return Itinerary(
            date = base.toLocalDate().toString(),
            districts = listOf("ville-marie"),
            stops = (0 until stops).map { i ->
                val start = base.plusHours(2L * i)
                Stop("s$i", StopType.Music, "Stop $i", districtId = "ville-marie", start = start, departure = start.plusHours(1))
            },
        )
    }

    private val bigTrip = trip(3)
    private val smallTrip = trip(1)

    @Before fun clean() = runBlocking {
        notifier.cancel(bigTrip)
        store.clear()
    }

    @After fun tearDown() = runBlocking {
        notifier.cancel(bigTrip)
        store.clear()
    }

    /**
     * EXPECTED TO FAIL until T1 (Q1/F14): `update` reads and writes in two DataStore transactions,
     * so concurrent updates (notification receiver + screen) overwrite each other.
     */
    @Test fun concurrentUpdatesAreNotLost() = runBlocking {
        val n = 40
        // Several rounds, so that a pass after T1 is not luck. A start gate releases all writers at once.
        repeat(5) { round ->
            store.save(UnderwayProgress(bigTrip, fromLat = 0.0))
            val gate = CompletableDeferred<Unit>()
            val jobs = (1..n).map {
                async(Dispatchers.Default) {
                    gate.await()
                    store.update { p -> p.copy(fromLat = (p.fromLat ?: 0.0) + 1) }
                }
            }
            gate.complete(Unit)
            jobs.awaitAll()
            // current() reads store.data: the last value DataStore committed to disk, not a local copy.
            assertEquals("round $round", n.toDouble(), store.current()!!.fromLat!!, 0.0)
        }
    }

    /**
     * EXPECTED TO FAIL until the Stamp-key fix (P1-03, proposed T17): GLOSSARY "Stamp: single,
     * unrepeatable mark". The same Event attended on another day must not earn a second Stamp.
     */
    @Test fun theSameEventNeverEarnsTwoStamps() = runBlocking {
        // A unique id per run, because there is no API to delete Stamps (they stay on the emulator).
        val id = "test-evt-${System.nanoTime()}"
        store.addStamps(listOf(EarnedStamp(id, "Concert", "ville-marie", "2026-11-21")))
        store.addStamps(listOf(EarnedStamp(id, "Concert", "ville-marie", "2026-11-28")))
        assertEquals(1, store.stamps.first().count { it.stopId == id })
    }

    /**
     * EXPECTED TO FAIL until T2 (Q2/F13): scheduling a new, shorter trip must leave only that trip's
     * alarms. Today `schedule` cancels only the new trip's indexes, so the old trip's later Stops keep
     * their alarms. Counted from `dumpsys alarm` (no root needed).
     */
    @Test fun schedulingANewTripRemovesTheOldTripsAlarms() {
        notifier.schedule(bigTrip)
        assertEquals("3 arrival checks + 2 leave reminders", 5, appAlarms())
        notifier.schedule(smallTrip)
        assertEquals("only the new trip's arrival check", 1, appAlarms())
    }

    /** Pending alarms of this app only: the "N pending alarms:" section, not the history sections. */
    private fun appAlarms(): Int {
        val all = shell("dumpsys alarm")
        val out = all.substringAfter("pending alarms:").substringBefore("LazyAlarmStore stats:")
        return Regex("walarm\\*:com\\.example\\.hackapp\\.(ARRIVAL_CHECK|LEAVE_REMINDER)").findAll(out).count()
    }

    private fun shell(cmd: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        return FileInputStream(pfd.fileDescriptor).bufferedReader().use { it.readText() }.also { pfd.close() }
    }
}
