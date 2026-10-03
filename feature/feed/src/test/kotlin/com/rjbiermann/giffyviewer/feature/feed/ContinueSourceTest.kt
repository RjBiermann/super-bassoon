package com.rjbiermann.giffyviewer.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** "Continue Watching" (§8, mobile): Room-only source identity — app-only
 *  title (AGENTS-APP lingo table), stable cache key, sortless surface (the
 *  sort chips row must stay hidden; withSort must not fork the identity). */
class ContinueSourceTest {
    @Test
    fun `Continue source - title and key and sortless identity`() {
        assertEquals("Continue Watching", FeedSource.Continue.title())
        assertEquals("continue:v1", FeedSource.Continue.keyBase)
        assertEquals("continue:v1", FeedSource.Continue.baseKey)
        assertEquals(0L, FeedSource.Continue.ttlMs)
        assertSame(FeedSource.Continue, FeedSource.Continue.withSort("latest"))
        assertEquals("", FeedSource.Continue.activeSort)
        assertEquals(emptyList<Pair<String, String>>(), FeedSource.Continue.sortOptions())
    }
}
