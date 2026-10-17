package com.rjbiermann.giffyviewer.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #50 stage 2: QuickSheet slider state-description format (issue #51). */
class SpeedStateDescriptionTest {
    @Test
    fun `state description formats two decimals with a times sign`() {
        assertEquals("0.25×", speedStateDescription(0.25f))
        assertEquals("1.00×", speedStateDescription(1f))
        assertEquals("1.25×", speedStateDescription(1.25f))
        assertEquals("2.00×", speedStateDescription(2f))
    }
}
