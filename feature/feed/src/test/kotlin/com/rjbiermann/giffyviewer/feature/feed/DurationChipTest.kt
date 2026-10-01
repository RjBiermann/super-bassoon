package com.rjbiermann.giffyviewer.feature.feed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** §8 duration chip bounds (client-side filter). */
class DurationChipTest {
    @Test
    fun `duration chips`() {
        assertTrue(durationIn(5.0, "lt10"))
        assertFalse(durationIn(15.0, "lt10"))
        assertTrue(durationIn(15.0, "10-30"))
        assertTrue(durationIn(45.0, "30-60"))
        assertTrue(durationIn(120.0, "1-5m"))
        assertTrue(durationIn(600.0, "gt5m"))
        // empty chip = no filter
        assertTrue(durationIn(600.0, ""))
        assertTrue(durationIn(5.0, ""))
    }
}
