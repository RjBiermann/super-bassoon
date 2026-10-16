package com.rjbiermann.giffyviewer.core.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #33: the banner is EXACTLY the not-online state (one rule, both mounts). */
class OfflineBannerTest {
    @Test
    fun `banner visible iff monitor says offline`() {
        assertFalse(offlineBannerVisible(true))
        assertTrue(offlineBannerVisible(false))
    }
}
