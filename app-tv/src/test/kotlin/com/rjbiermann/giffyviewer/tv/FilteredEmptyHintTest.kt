package com.rjbiermann.giffyviewer.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** SLICE-12: the filtered-empty hint must tell the truth about the filter. */
class FilteredEmptyHintTest {
    @Test
    fun `effective any never claims an orientation filter`() {
        val hint = filteredEmptyHint("any")
        assertEquals("No videos match your filters", hint)
        assertFalse(hint.contains("orientation", ignoreCase = true))
    }

    @Test
    fun `strict value names the actual filter and value`() {
        assertEquals(
            "No videos match your filters — orientation: horizontal",
            filteredEmptyHint("horizontal"),
        )
        assertEquals(
            "No videos match your filters — orientation: vertical",
            filteredEmptyHint("vertical"),
        )
    }
}
