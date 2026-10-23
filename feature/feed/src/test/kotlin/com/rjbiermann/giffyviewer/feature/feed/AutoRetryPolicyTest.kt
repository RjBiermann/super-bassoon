package com.rjbiermann.giffyviewer.feature.feed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #63 follow-up: the auto-retry budget must survive a reconnect. While
 * offline every attempt burns budget on a failure that could never succeed (each
 * one also pays interceptor backoff), so the design's "airplane off → card clears
 * without a tap" needs the offline→online transition to re-arm the budget — while
 * a continuously-online failure keeps the per-appearance cap so it cannot loop.
 */
class AutoRetryPolicyTest {
    @Test
    fun `a reconnect resets the auto-retry budget`() {
        val budget = AutoRetryBudget(isOnline = false)
        assertTrue(budget.take())
        assertTrue(budget.take())
        assertFalse("two offline attempts exhaust the per-appearance budget", budget.take())

        budget.isOnline = true // airplane off
        assertTrue("the reconnect itself may retry", budget.take())
        // The reset re-arms the same per-appearance cap — one more attempt here,
        // never an unbounded loop.
        assertTrue(budget.take())
        assertFalse("the cap still holds after the reset", budget.take())
    }

    @Test
    fun `a continuously online failure keeps the two-attempt cap`() {
        val budget = AutoRetryBudget(isOnline = true)
        assertTrue(budget.take())
        assertTrue(budget.take())
        assertFalse(budget.take())
    }

    @Test
    fun `a success re-arms the budget`() {
        val budget = AutoRetryBudget(isOnline = true)
        repeat(AUTO_RETRY_MAX) { budget.take() }
        budget.reset()
        assertTrue(budget.take())
    }
}
