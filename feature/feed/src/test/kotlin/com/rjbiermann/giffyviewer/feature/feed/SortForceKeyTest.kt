package com.rjbiermann.giffyviewer.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * FeedViewModel pending-force key (AGENTS.md "Open items", fixed with #65's
 * batch): a sort click records the key its next fetch is owed a TTL bypass
 * for, and the gifs flow reads that key off the feed it is about to open
 * (`source.withSort(sort)`). Recording the sortless [FeedSource.baseKey]
 * instead never matched for the sortable surfaces — Search/Creator/Niche key
 * their cache on `keyBase`, which carries the `:sort=` suffix — so the bypass
 * was silently lost whenever the sorted base was already cached.
 */
class SortForceKeyTest {
    @Test
    fun `the force key is the opened feed's cache base, not the prefs identity`() {
        val source = FeedSource.Creator("perkyandpink")
        val opened = source.withSort("latest")

        assertEquals(opened.keyBase, source.sortForceKey("latest"))
        assertEquals("user:v2:perkyandpink:sort=latest", source.sortForceKey("latest"))
        assertNotEquals("the sortless prefs base is a different key", source.baseKey, source.sortForceKey("latest"))
    }

    @Test
    fun `every sortable surface forces its own sorted base`() {
        assertEquals(FeedSource.Search("cats", "top7").keyBase, FeedSource.Search("cats").sortForceKey("top7"))
        assertEquals(FeedSource.Niche("1", "n", "latest").keyBase, FeedSource.Niche("1", "n").sortForceKey("latest"))
    }

    @Test
    fun `a source that ignores sorts still records its own cache base`() {
        // withSort is identity here, so the recorded key equals the read key —
        // the bypass stays a one-shot refresh on the same base.
        assertEquals(FeedSource.Trending.keyBase, FeedSource.Trending.sortForceKey("latest"))
        assertEquals(FeedSource.Favorites.keyBase, FeedSource.Favorites.sortForceKey("latest"))
    }
}
