package com.rjbiermann.giffyviewer.search

import com.rjbiermann.giffyviewer.core.network.CreatorSearchItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Explore empty-state sections (issue #11): creator-strip page merge —
 *  dedup by username (upstream pagination overlaps: page n re-lists page
 *  n-1), leak-zero blocked drop, and the no-growth = exhausted decision. */
class SearchExploreSectionsTest {
    private fun creator(name: String): CreatorSearchItemDto = CreatorSearchItemDto(username = name)

    @Test
    fun `overlapping pages dedup by username and report growth until exhaustion`() {
        val page1 = listOf(creator("a"), creator("b"))
        val page2 = listOf(creator("b"), creator("c"))

        val (rows1, exhausted1) = exploreCreatorRows(emptyList(), page1) { true }
        assertFalse(exhausted1)
        assertEquals(listOf("a", "b"), rows1.map { it.username })

        // Server carries no page count; page 3 re-listing page 2 = complete.
        val (rows2, exhausted2) = exploreCreatorRows(rows1, page2) { true }
        assertFalse(exhausted2)
        assertEquals(listOf("a", "b", "c"), rows2.map { it.username })

        val (rows3, exhausted3) = exploreCreatorRows(rows2, page2) { true }
        assertTrue("no growth means the list is complete", exhausted3)
        assertEquals(listOf("a", "b", "c"), rows3.map { it.username })
    }

    @Test
    fun `blocked creator rows drop from the strip and a fully dropped page ends it`() {
        val page = listOf(creator("blocked"), creator("ok"))

        val (rows, exhausted) = exploreCreatorRows(emptyList(), page) { it.username != "blocked" }
        assertFalse(exhausted)
        assertEquals(listOf("ok"), rows.map { it.username })

        val (blockedOnly, exhaustedAll) = exploreCreatorRows(rows, listOf(creator("blocked"))) { it.username != "blocked" }
        assertTrue("nothing grew — walk the same rule the server-exhaustion path uses", exhaustedAll)
        assertEquals(listOf("ok"), blockedOnly.map { it.username })
    }
}
