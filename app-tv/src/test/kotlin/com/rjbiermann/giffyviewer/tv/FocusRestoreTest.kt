package com.rjbiermann.giffyviewer.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #13 §4.3: the re-arm decision for a nav-reentered D-pad surface —
 * the saved card focus target (pure; the composable wiring consumes it once
 * per content delivery: on entry (re)appearance only, never on appends).
 */
class FocusRestoreTest {
    @Test
    fun `a feasible saved card re-arms itself`() {
        assertEquals(3, focusTargetFor(3, 40))
        // Boundary: the LAST loaded card is still a valid restore target.
        assertEquals(9, focusTargetFor(9, 10))
    }

    @Test
    fun `a fresh entry or a regrown-shorter pool falls back to card 0`() {
        assertEquals(0, focusTargetFor(-1, 40))
        assertEquals(0, focusTargetFor(39, 1))
    }
}
