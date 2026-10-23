package com.rjbiermann.giffyviewer.core.network.rate

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #63 device re-gate: the breaker's cooldown is time-based and nothing
 * else generates traffic while it is parked, so the reconnect retries (fired by
 * AutoRetryEffect right after airplane-mode ends) all hit the synthetic
 * "HTTP 503 circuit open" and the error card latched for the full 5 minutes.
 * A connectivity flip is the one event that proves the breaker should re-probe.
 */
class BreakerReconnectResetTest {
    private fun openBreaker(
        threshold: Int = 3,
        duration: Long = 5 * 60 * 1_000,
    ): CircuitBreaker =
        CircuitBreaker(failureThreshold = threshold, openDurationMs = duration).apply {
            repeat(threshold) { recordFailure() }
        }

    @Test
    fun `offline to online flip lets the next attempt through`() {
        val breaker = openBreaker()
        val reset = BreakerReconnectReset(breaker, MutableStateFlow(false))

        reset.onConnectivityChanged(false)
        assertTrue("still offline — the breaker must stay open", breaker.isOpen())

        reset.onConnectivityChanged(true)
        assertFalse(breaker.isOpen())
        breaker.checkOrThrow() // must not throw
    }

    @Test
    fun `repeated online events only reset on the flip`() {
        val breaker = openBreaker()
        val reset = BreakerReconnectReset(breaker, MutableStateFlow(false))
        reset.onConnectivityChanged(true)

        // The network was never lost again: a further breaker failure streak
        // must stand — only a NEW offline->online flip earns another reset.
        repeat(3) { breaker.recordFailure() }
        reset.onConnectivityChanged(true)
        assertTrue(breaker.isOpen())
    }

    @Test
    fun `going online with a closed breaker changes nothing`() {
        val breaker = CircuitBreaker()
        val reset = BreakerReconnectReset(breaker, MutableStateFlow(true))

        reset.onConnectivityChanged(true)
        reset.onConnectivityChanged(false)
        reset.onConnectivityChanged(true)

        assertFalse(breaker.isOpen())
        breaker.checkOrThrow()
    }

    @Test
    fun `three consecutive failures still open the breaker`() {
        val breaker = openBreaker()
        assertTrue(breaker.isOpen())
        assertTrue(breaker.retryAtEpochMs() > 0)
    }

    @Test
    fun `observe resets on the flow flip`() =
        runTest {
            val breaker = openBreaker()
            val online = MutableStateFlow(false)
            val reset = BreakerReconnectReset(breaker, online)
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { reset.observe() }
            assertTrue(breaker.isOpen())

            online.value = true
            assertFalse(breaker.isOpen())
            job.cancel()
        }
}
