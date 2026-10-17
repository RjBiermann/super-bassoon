package com.rjbiermann.giffyviewer.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #47 slice 1: the pure join/leave decisions the feed-header button rides. */
class NicheJoinStateTest {
    @Test
    fun `label flips site lingo join niche - leave niche`() {
        assertEquals("Join Niche", joinNicheLabel(joined = false))
        assertEquals("Leave Niche", joinNicheLabel(joined = true))
    }

    @Test
    fun `successful write flips membership only`() {
        assertEquals(setOf("n1"), joinedAfter(current = emptySet(), id = "n1", joined = true))
        assertEquals(emptySet<String>(), joinedAfter(current = setOf("n1"), id = "n1", joined = false))
    }

    @Test
    fun `flip keeps other niches`() {
        assertEquals(setOf("n1", "n2"), joinedAfter(current = setOf("n1"), id = "n2", joined = true))
        assertEquals(setOf("n2"), joinedAfter(current = setOf("n1", "n2"), id = "n1", joined = false))
    }

    @Test
    fun `failure surfaces an honest non-blank message`() {
        assertTrue(JOIN_UPDATE_FAILED.isNotBlank())
    }

    @Test
    fun `pending opens on start and resolves on completion both ways`() {
        // The button must guard double-taps while the write is in flight
        // (an unflipped button invites re-presses; repeat writes raced the
        // joined-state non-determinism seen in the #47 device pass).
        assertEquals(setOf("n1"), pendingAfterStart(emptySet(), "n1"))
        assertEquals(emptySet<String>(), pendingResolved(setOf("n1"), "n1"))
    }
}
