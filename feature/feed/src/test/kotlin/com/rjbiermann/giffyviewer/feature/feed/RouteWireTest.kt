package com.rjbiermann.giffyviewer.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Issue #34 device crash regression: route-wire values containing `/` broke
 * nav's path parsing ("…/0 cannot be found in the navigation graph"). The
 * three custom NavTypes wrap their wire in URL-safe Base64 — slash-free by
 * alphabet.
 */
class RouteWireTest {
    @Test
    fun `feedSource wire is slash-free and round-trips`() {
        val source = FeedSource.Search(query = "teen/ass?five=six", sort = "score")
        val wire = feedSourceNavType.serializeAsValue(source)
        assertFalse('/' in wire)
        assertEquals(source, feedSourceNavType.parseValue(wire))
    }

    @Test
    fun `niche wire round-trips with slashy ids`() {
        val niche = FeedSource.Niche(id = "petite/chicks", name = "Petite/Chicks")
        val wire = nicheNavType.serializeAsValue(niche)
        assertFalse('/' in wire)
        assertEquals(niche, nicheNavType.parseValue(wire))
    }
}
