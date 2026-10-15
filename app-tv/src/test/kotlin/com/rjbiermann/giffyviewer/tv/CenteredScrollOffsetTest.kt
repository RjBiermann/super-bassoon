package com.rjbiermann.giffyviewer.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * #14 §B: the pure centering math behind the focused-row scrollToItem offset —
 * the offset leans an item of itemWidthPx toward the middle of the content
 * area (viewport minus the 16dp leading padding on both sides).
 */
class CenteredScrollOffsetTest {
    @Test
    fun `centers a card in the padded viewport`() {
        // (800 - 2×50 - 300) / 2 = 200 px past the padded start.
        assertEquals(200, centeredScrollOffset(800, 300, 50, 48))
    }

    @Test
    fun `inside the deadband returns null so small focus steps don't jitter`() {
        // (800 - 100 - 636) / 2 = 32 ≤ 48 → no scroll.
        assertNull(centeredScrollOffset(800, 636, 50, 48))
        // EXACT-deadband boundary also counts as "already centered".
        assertNull(centeredScrollOffset(800, 604, 50, 48))
    }

    @Test
    fun `negative offset pulls an early card right - scrollToItem accepts it`() {
        // Item wider than the content area → leans as far right as it can.
        assertEquals(-100, centeredScrollOffset(800, 900, 50, 48))
    }

    @Test
    fun `degenerate inputs never scroll`() {
        assertNull(centeredScrollOffset(0, 300, 50, 48))
        assertNull(centeredScrollOffset(800, 0, 50, 48))
    }
}
