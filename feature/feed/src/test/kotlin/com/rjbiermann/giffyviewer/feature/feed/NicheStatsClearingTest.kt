package com.rjbiermann.giffyviewer.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Issue #31 (niche header): the gate that clears the niche stats StateFlow on
 * a feed switch — every NON-niche source maps to the null clear-call
 * (refreshNicheStats(null)), exactly the contract refreshCreatorStats's null
 * gate enforces for the creator header. A niche feed keeps its id.
 */
class NicheStatsClearingTest {
    @Test
    fun `every non-niche source clears the niche header`() {
        assertNull(nicheSourceId(FeedSource.Trending))
        assertNull(nicheSourceId(FeedSource.ForYou))
        assertNull(nicheSourceId(FeedSource.Favorites))
        assertNull(nicheSourceId(FeedSource.Liked))
        assertNull(nicheSourceId(FeedSource.TopThisWeek))
        assertNull(nicheSourceId(FeedSource.TopRanged(range = "top7")))
        assertNull(nicheSourceId(FeedSource.Search(query = "cats")))
        assertNull(nicheSourceId(FeedSource.Creator(username = "sweety.yuko")))
        assertNull(nicheSourceId(FeedSource.Custom(id = 1, name = "blend", refs = emptyList())))
        // The niche feed itself refreshes (and keeps) the header.
        assertEquals("cats", nicheSourceId(FeedSource.Niche(id = "cats", name = "Cats")))
    }
}
