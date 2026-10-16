package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.model.Gif
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

    @Test
    fun `gif list wire survives descriptions with slashes`() {
        val gifs =
            listOf(aGif(description = "link https://onlyfans.com/x/c558 in bio"), aGif(id = "plain", description = null))
        val wire = gifListNavType.serializeAsValue(gifs)
        assertFalse('/' in wire)
        assertEquals(gifs, gifListNavType.parseValue(wire))
    }
}

private fun aGif(
    id: String = "testid",
    description: String?,
): Gif =
    Gif(
        id = id,
        userName = "creator_x",
        description = description,
        tags = listOf("Solo"),
        likes = 1,
        views = 2,
        durationSeconds = 3.0,
        hasAudio = false,
        width = 1080,
        height = 1920,
        createDateEpoch = 0,
        published = true,
        avgColor = "#000000",
        sdUrl = null,
        hdUrl = null,
        posterUrl = null,
        niches = emptyList(),
    )
