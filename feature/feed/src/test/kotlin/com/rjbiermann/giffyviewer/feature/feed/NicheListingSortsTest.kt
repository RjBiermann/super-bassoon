package com.rjbiermann.giffyviewer.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #47 probe-gated remainder (DESIGN-47SORTS): the full 5-order sort menu
 * on the niches LISTING (`v2/niches/search/previews`, no query=). All five
 * orders live-verified 2026-10-17 (probe budget 4: token + posts +
 * alphabetical_asc + alphabetical_desc — all 200). The menu order + labels
 * mirror the site's own /niches sort UI (REPORT-niches-parity A5: Subscribers ·
 * Posts · Name: A-Z · Name: Z-A + Randomize).
 */
class NicheListingSortsTest {
    @Test
    fun `all five verified orders ship in site-menu order`() {
        assertEquals(
            listOf("subscribers", "posts", "alphabetical_asc", "alphabetical_desc", "random"),
            nicheListingSorts.map { it.first },
        )
    }

    @Test
    fun `labels match the site sort menu`() {
        assertEquals(
            listOf("Subscribers", "Posts", "Name: A-Z", "Name: Z-A", "Randomize"),
            nicheListingSorts.map { it.second },
        )
    }
}
