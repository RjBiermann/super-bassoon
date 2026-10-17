package com.rjbiermann.giffyviewer.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #46 part 2: home rows in the "Couldn't load" state auto-retry when
 * they (re)compose or the network returns — silently, capped so a breaker
 * outage can't turn the row into a retry loop. The gate rule:
 * up to [TvHomeScreenKt.AUTO_RETRY_MAX] consecutive auto-retries per row
 * appearance; the counter resets only when a load succeeds (offline/re-entry
 * resets naturally with the row's own remember state).
 */
class FeedRowAutoRetryTest {
    @Test
    fun `a freshly appeared error row auto-retries`() {
        assertTrue(shouldAutoRetry(0))
    }

    @Test
    fun `one failed auto-retry still allows one more`() {
        assertTrue(shouldAutoRetry(1))
    }

    @Test
    fun `two consecutive auto-retries exhaust the gate`() {
        assertFalse(shouldAutoRetry(2))
    }

    @Test
    fun `the gate stays closed past the cap`() {
        assertFalse(shouldAutoRetry(5))
    }

    @Test
    fun `the cap is two retries`() {
        assertEquals(2, AUTO_RETRY_MAX)
    }
}
