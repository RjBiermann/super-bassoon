package com.rjbiermann.giffyviewer.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mobile For-You default slice (2026-10): token-seeded landing + offline
 *  fallback rule (a network-live seeded For You with no content reverts to
 *  Trending — airplane-mode gate 3 wants cached tiles, not an error state). */
class ForYouDefaultTest {
    @Test
    fun `token present seeds ForYou - absent seeds Trending`() {
        assertEquals(FeedRepository.defaultLandingSource(tokenPresent = true), FeedSource.ForYou)
        assertEquals(FeedRepository.defaultLandingSource(tokenPresent = false), FeedSource.Trending)
    }

    @Test
    fun `fallback reverts only a seeded un-navigated ForYou without content`() {
        // Seeded For You, initial refresh failed, nothing cached → revert.
        assertTrue(FeedRepository.shouldRevertToTrending(FeedSource.ForYou, userNavigated = false, hasContent = false))
        // User already navigated → never hijack their choice.
        assertFalse(FeedRepository.shouldRevertToTrending(FeedSource.ForYou, userNavigated = true, hasContent = false))
        // Content cached after all → render it, don't reroute.
        assertFalse(FeedRepository.shouldRevertToTrending(FeedSource.ForYou, userNavigated = false, hasContent = true))
        // Other sources keep their own error/empty surfacing.
        assertFalse(FeedRepository.shouldRevertToTrending(FeedSource.Trending, userNavigated = false, hasContent = false))
        assertFalse(
            FeedRepository.shouldRevertToTrending(FeedSource.Creator("x"), userNavigated = false, hasContent = false),
        )
    }
}
