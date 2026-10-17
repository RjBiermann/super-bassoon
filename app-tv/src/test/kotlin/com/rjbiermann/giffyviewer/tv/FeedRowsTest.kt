package com.rjbiermann.giffyviewer.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #45: source-feed pager → home-style band rows (the chunking math). */
class FeedRowsTest {
    @Test
    fun `an item count chunks into 4-card bands`() {
        assertEquals(0, feedRowCount(0))
        assertEquals(1, feedRowCount(1))
        assertEquals(1, feedRowCount(4))
        assertEquals(2, feedRowCount(5))
        assertEquals(3, feedRowCount(9))
        assertEquals(10, feedRowCount(40))
    }

    @Test
    fun `the last band is partial and past-the-end bands are empty`() {
        assertEquals(4, feedRowCardCount(5, 0))
        assertEquals(1, feedRowCardCount(5, 1))
        assertEquals(0, feedRowCardCount(5, 2))
        assertEquals(4, feedRowCardCount(40, 9))
    }
}
