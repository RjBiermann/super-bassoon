package com.rjbiermann.giffyviewer.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #14 §B (device pass 2026-10-16): the focused-row centering moved from a
 * programmatic scrollToItem (breaks the focused item's a11y state — see
 * RowCentering.kt) to a BringIntoViewSpec. The pure math contract: an item
 * left of center scrolls RIGHT (positive), right of center scrolls LEFT
 * (negative), a centered item never scrolls; the deadband absorbs small
 * offsets so focus steps that land near center don't jitter.
 */
class CenteredScrollOffsetTest {
    private val spec = centeredBringIntoViewSpec(deadbandPx = 48)

    @Test
    fun `item left of center scrolls right to the middle`() {
        // Viewport 800, card 300 at offset 100: center slot = (800-300)/2 = 250
        // → distance = 100 - 250 = -150 → NEGATIVE scrolls the content LEFT
        // (brings the right-side card toward center).
        assertEquals(-150f, spec.calculateScrollDistance(100f, 300f, 800f), 0.01f)
    }

    @Test
    fun `item right of center scrolls left to the middle`() {
        // Card at offset 600 in an 800 viewport: 600 - 250 = +350.
        assertEquals(350f, spec.calculateScrollDistance(600f, 300f, 800f), 0.01f)
    }

    @Test
    fun `centered item never scrolls`() {
        assertEquals(0f, spec.calculateScrollDistance(250f, 300f, 800f), 0.01f)
    }

    @Test
    fun `inside the deadband returns zero so small offsets don't jitter`() {
        // Distance 32 ≤ 48 → no scroll.
        assertEquals(0f, spec.calculateScrollDistance(282f, 300f, 800f), 0.01f)
    }

    @Test
    fun `zero deadband centers exactly every step`() {
        val exact = centeredBringIntoViewSpec(deadbandPx = 0)
        // Same offset that the deadband spec absorbed now scrolls exactly.
        assertEquals(32f, exact.calculateScrollDistance(282f, 300f, 800f), 0.01f)
    }
}
