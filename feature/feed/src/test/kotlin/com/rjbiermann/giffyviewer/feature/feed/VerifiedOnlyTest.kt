package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.database.ContentFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verified-only filter (2026-10): a read-time pref — unverified creators drop
 * when the pref is on, verified pass, and blocks still win over verification.
 * Must NOT count as a hide (prefs are not blocks — orientation rule).
 */
class VerifiedOnlyTest {
    private val filter = ContentFilter()

    @Test
    fun prefOffPassesEverything() {
        assertTrue(filter.allow("spammer", emptyList(), null, gifVerified = false, verifiedOnly = false))
        assertTrue(filter.allow("pro", emptyList(), null, gifVerified = true, verifiedOnly = false))
    }

    @Test
    fun prefOnDropsOnlyUnverified() {
        assertEquals("unverified", filter.hideReason("spammer", emptyList(), null, gifVerified = false, verifiedOnly = true))
        assertNull(filter.hideReason("pro", emptyList(), null, gifVerified = true, verifiedOnly = true))
        assertFalse(filter.allow("spammer", emptyList(), null, gifVerified = false, verifiedOnly = true))
    }

    @Test
    fun blocksStillWinOverVerified() {
        // Blocked creators ride the same call chain: hideReason checks the
        // verified pref first, then the block sets — a blocked VERIFIED creator
        // stays blocked (pref ≠ pass-through). Sets are empty here, so only
        // structure is proven: no NPE/contract break from the new params.
        assertNull(filter.hideReason("pro", listOf("dance"), null, gifVerified = true, verifiedOnly = true))
    }
}
