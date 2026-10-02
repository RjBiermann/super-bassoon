package com.rjbiermann.giffyviewer.feature.feed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Inline-autoplay settle rule (AGENTS-PLAYER spec): first tile ≥50% visible. */
class InlineSettleTest {
    @Test
    fun `half-visible and fully-visible tiles settle`() {
        // 800px viewport, 780px tile straddling the fold: 720 visible ≥ 390.
        assertTrue(isSettled(offset = -60, size = 780, viewport = 800))
        assertTrue(isSettled(offset = 0, size = 400, viewport = 800))
    }

    @Test
    fun `hover-bys and off-view tiles do not settle`() {
        // Barely peeking: 20/400 visible.
        assertFalse(isSettled(offset = -380, size = 400, viewport = 800))
        // Below the fold.
        assertFalse(isSettled(offset = 810, size = 400, viewport = 800))
        // Exactly half — the threshold boundary settles (≥).
        assertTrue(isSettled(offset = -200, size = 400, viewport = 800))
    }

    @Test
    fun `placeholder rows never settle`() {
        assertFalse(isSettled(offset = 0, size = 0, viewport = 800))
    }
}
