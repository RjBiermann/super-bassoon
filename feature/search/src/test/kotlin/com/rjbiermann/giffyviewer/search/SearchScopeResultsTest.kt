package com.rjbiermann.giffyviewer.search

import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.model.NicheRef
import com.rjbiermann.giffyviewer.core.network.NichePreviewNicheDto
import com.rjbiermann.giffyviewer.core.network.NichePreviewRowDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Full-scope search scope-load logic: VM cache cap + niche-row leak-zero drop. */
class SearchScopeResultsTest {
    private fun gif(id: String) =
        Gif(
            id = id,
            userName = "u$id",
            description = null,
            tags = emptyList(),
            likes = 0,
            views = 0,
            durationSeconds = 0.0,
            hasAudio = false,
            width = 0,
            height = 0,
            createDateEpoch = 0,
            published = true,
            avgColor = "#000000",
            sdUrl = null,
            hdUrl = null,
            posterUrl = null,
            niches = emptyList(),
        )

    @Test
    fun `cappedInsert keeps the twelve most recent queries`() {
        var map = emptyMap<String, Int>()
        repeat(12) { n -> map = cappedInsert(map, "q$n", n) }
        assertEquals(12, map.size)
        assertEquals(11, map["q11"])
        // 13th insert evicts the oldest, keeps the rest.
        map = cappedInsert(map, "q12", 12)
        assertEquals(12, map.size)
        assertNull("q0 evicted", map["q0"])
        assertEquals(12, map["q12"])
    }

    @Test
    fun `niche rows drop blank ids and blocked previews, keep unblocked`() {
        val previews =
            listOf(
                NichePreviewRowDto(NichePreviewNicheDto(id = "", name = "no id")),
                NichePreviewRowDto(niche = null),
                NichePreviewRowDto(NichePreviewNicheDto(id = "n1", name = "Blocked")), // blocked gif
                NichePreviewRowDto(NichePreviewNicheDto(id = "n2", name = null)),
            )
        val rows = nicheRows(previews) { it.id != "n1" }
        assertEquals(2, rows.size)
        assertEquals(NicheRef("n2", "n2"), rows[1].first) // name falls back to id
        assertNull(rows[1].second) // gif-less niche row survives (no preview to block on)
    }
}
