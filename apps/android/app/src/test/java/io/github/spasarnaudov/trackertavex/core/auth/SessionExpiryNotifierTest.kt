package io.github.spasarnaudov.trackertavex.core.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionExpiryNotifierTest {

    @Test
    fun `notifyExpired emits an event to an already-subscribed collector`() = runTest {
        val notifier = SessionExpiryNotifier()
        var eventCount = 0
        val job = launch { notifier.events.collect { eventCount++ } }
        runCurrent()

        notifier.notifyExpired()
        runCurrent()

        assertEquals(1, eventCount)
        job.cancel()
    }

    @Test
    fun `an event emitted before any collector subscribes is not delivered later`() = runTest {
        val notifier = SessionExpiryNotifier()
        notifier.notifyExpired()

        var eventCount = 0
        val job = launch { notifier.events.collect { eventCount++ } }
        runCurrent()

        assertEquals(0, eventCount)
        job.cancel()
    }
}
