package com.rjbiermann.giffyviewer.core.network.rate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Edge-case transitions of the plan-§4 circuit breaker (3 consecutive
 * failures → open 5 min; any success resets): reset mid-sequence, expiry,
 * success-while-open (the half-open probe equivalent), and failure-extension
 * semantics while open.
 */
class CircuitBreakerTest {
    private var virtualNow = 0L

    private fun breaker(
        threshold: Int = 3,
        duration: Long = 60_000L,
    ) = CircuitBreaker(
        failureThreshold = threshold,
        openDurationMs = duration,
        now = { virtualNow },
    )

    @Test
    fun `opens exactly at the failure threshold, not before`() {
        val b = breaker()
        b.recordFailure()
        virtualNow = 1_000
        b.recordFailure()
        assertFalse("2 failures must not open the circuit", b.isOpen())
        b.recordFailure()
        assertTrue(b.isOpen())
        assertEquals(virtualNow + 60_000, b.retryAtEpochMs())
    }

    @Test
    fun `a success resets consecutive failures to zero`() {
        val b = breaker()
        b.recordFailure()
        b.recordFailure()
        b.recordSuccess()
        b.recordFailure()
        b.recordFailure()
        // consecutive count was never 3 in a row → still closed
        assertFalse(b.isOpen())
    }

    @Test
    fun `one success closes an already-open circuit (half-open probe effect)`() {
        val b = breaker()
        repeat(3) { b.recordFailure() }
        assertTrue(b.isOpen())
        b.recordSuccess()
        assertFalse("a success must re-arm the circuit", b.isOpen())
        assertEquals(0, b.retryAtEpochMs())
    }

    @Test
    fun `circuit passes again once the open duration has elapsed`() {
        val b = breaker(duration = 60_000)
        repeat(3) { b.recordFailure() }
        assertTrue(b.isOpen())
        virtualNow = 60_000 // exactly at retryAt → no longer open
        assertFalse(b.isOpen())
    }

    @Test
    fun `checkOrThrow throws CircuitOpenException carrying retryAt`() {
        val b = breaker()
        repeat(3) { b.recordFailure() }
        val thrown =
            assertThrows(CircuitOpenException::class.java) { b.checkOrThrow() }
        assertEquals(b.retryAtEpochMs(), thrown.retryAtEpochMs)
    }

    @Test
    fun `checkOrThrow does not throw while closed`() {
        breaker().checkOrThrow() // no assertion needed — must not throw
    }

    @Test
    fun `failures while open re-open with a later retryAt`() {
        val b = breaker(duration = 60_000)
        repeat(3) { b.recordFailure() }
        val firstRetryAt = b.retryAtEpochMs()
        virtualNow = 5_000
        // failures while open still meet the threshold → openedUntil is pushed out
        b.recordFailure()
        assertTrue(b.retryAtEpochMs() > firstRetryAt)
        assertEquals(65_000, b.retryAtEpochMs())
    }

    @Test
    fun `higher threshold requires that many consecutive failures`() {
        val b = breaker(threshold = 5)
        repeat(4) { b.recordFailure() }
        assertFalse(b.isOpen())
        b.recordFailure()
        assertTrue(b.isOpen())
    }
}
