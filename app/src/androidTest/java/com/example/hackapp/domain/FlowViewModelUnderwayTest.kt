package com.example.hackapp.domain

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hackapp.HackappApplication
import com.example.hackapp.ui.theme.StopType
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T1, instrumented: Underway actions call back only once the write is saved and visible, with the
 * saved progress. WARNING: uses the app's own "trip" DataStore, so it wipes any trip in progress.
 */
@RunWith(AndroidJUnit4::class)
class FlowViewModelUnderwayTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HackappApplication
    private val zone = ZoneId.of("America/Toronto")
    private lateinit var vm: FlowViewModel

    @Before fun setUp() = runBlocking {
        app.underwayStore.clear()
        vm = withContext(Dispatchers.Main) { FlowViewModel(app, app.cityServices) }
        val base = LocalDateTime.now(zone).plusHours(2).withSecond(0).withNano(0)
        withContext(Dispatchers.Main) {
            vm.newDraft()
            (0 until 2).forEach { i ->
                val start = base.plusHours(2L * i)
                vm.addStop(Stop("cb$i", StopType.Music, "Stop $i", districtId = "ville-marie", start = start, departure = start.plusHours(1)))
            }
        }
    }

    @After fun tearDown() = runBlocking {
        app.underwayStore.current()?.let { app.notifier.cancel(it.itinerary) }
        app.underwayStore.clear()
    }

    /** Runs an action on the main thread and waits for its callback. */
    private suspend fun call(action: (onDone: (UnderwayProgress?) -> Unit) -> Unit): UnderwayProgress? {
        val done = CompletableDeferred<UnderwayProgress?>()
        withContext(Dispatchers.Main) { action { done.complete(it) } }
        return withTimeout(5_000) { done.await() }
    }

    @Test fun callbacksSeeTheSavedProgress() = runBlocking {
        val started = call { vm.start(it) }
        assertNotNull(started)
        assertEquals(0, started!!.index)
        assertEquals(started, vm.underwayLoad.value?.progress)

        val arrived = call { vm.arrived(0, it) }
        assertEquals(true, arrived!!.atStop)
        assertEquals(arrived, vm.underwayLoad.value?.progress)

        val left = call { vm.finishStop(0, StopStatus.SKIPPED, it) }
        assertEquals(1, left!!.index)
        assertEquals(StopStatus.SKIPPED, left.statuses[0])
        assertEquals(false, left.atStop)
        assertEquals(left, vm.underwayLoad.value?.progress)
        assertEquals(left, app.underwayStore.current())
    }

    @Test fun anActionForAStopThatIsNoLongerCurrentIsNotApplied() = runBlocking {
        call { vm.start(it) }
        call { vm.finishStop(0, StopStatus.SKIPPED, it) }
        // The page still showed Stop 0 (e.g. a notification moved on first): nothing more is skipped.
        val stale = call { vm.finishStop(0, StopStatus.SKIPPED, it) }
        assertEquals(1, stale!!.index)
        assertEquals(StopStatus.PENDING, stale.statuses[1])
        // The person is told why (the message itself is a Toast; the counter is what we can observe).
        assertEquals(1, vm.droppedActions)
    }

    @Test fun anAppliedActionGivesNoDroppedFeedback() = runBlocking {
        call { vm.start(it) }
        call { vm.arrived(0, it) }
        assertEquals(0, vm.droppedActions)
    }

    @Test fun aDoubleTapWhileSavingAppliesOnce() = runBlocking {
        call { vm.start(it) }
        val first = CompletableDeferred<UnderwayProgress?>()
        var secondCalled = false
        withContext(Dispatchers.Main) {
            // Two taps in the same frame: the second arrives while the first is still saving.
            vm.finishStop(0, StopStatus.SKIPPED) { first.complete(it) }
            vm.finishStop(0, StopStatus.SKIPPED) { secondCalled = true }
        }
        val saved = withTimeout(5_000) { first.await() }
        assertEquals(1, saved!!.index)
        assertEquals(StopStatus.PENDING, saved.statuses[1])
        assertEquals(saved, app.underwayStore.current())
        assertEquals(false, secondCalled)
        // Ignored, not dropped as stale: no misleading message.
        assertEquals(0, vm.droppedActions)
    }
}
