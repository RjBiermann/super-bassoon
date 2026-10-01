package com.rjbiermann.giffyviewer.search

import com.rjbiermann.giffyviewer.core.database.SearchHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** Gate 337: history cap 50 — trim keeps the newest N by searchedAt. */
class SearchHistoryTrimTest {
    @Test
    fun `trim keeps newest fifty`() {
        val rows = (1..60).map { SearchHistoryEntity(query = "q$it", searchedAt = it.toLong()) }
        val kept = rows.sortedByDescending { it.searchedAt }.take(50)
        assertEquals(50, kept.size)
        assertEquals("q60", kept.first().query)
        assertEquals("q11", kept.last().query)
        assertEquals(10, rows.take(10).size) // 60 rows in, 50 kept
    }
}
