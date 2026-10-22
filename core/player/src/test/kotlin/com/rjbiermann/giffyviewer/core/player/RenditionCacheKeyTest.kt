package com.rjbiermann.giffyviewer.core.player

import com.rjbiermann.giffyviewer.core.model.Gif
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Issue #56: the SimpleCache key must be rendition-specific. The pick that
 *  resolves to the SD stream (TV focus preview / data-saver watch) may not
 *  share a key with an HD pick, or a cached SD rendition replays under
 *  "Highest". */
class RenditionCacheKeyTest {
    private fun gif(
        sdUrl: String? = "https://cdn/g1_sd.mp4",
        hdUrl: String? = "https://cdn/g1_hd.mp4",
    ): Gif =
        Gif(
            id = "g1",
            userName = "u",
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
            avgColor = "",
            sdUrl = sdUrl,
            hdUrl = hdUrl,
            posterUrl = null,
            niches = emptyList(),
        )

    @Test
    fun `sd pick caches under a distinct sd-tier key`() {
        assertEquals("g1:sd", gif().renditionCacheKey(gif().sdUrl!!))
    }

    @Test
    fun `hd pick caches under a distinct hd-tier key`() {
        assertEquals("g1:hd", gif().renditionCacheKey(gif().hdUrl!!))
    }

    @Test
    fun `sd-cached key never serves an hd request for the same gif`() {
        val g = gif()
        assertNotEquals(g.renditionCacheKey(g.sdUrl!!), g.renditionCacheKey(g.hdUrl!!))
    }

    @Test
    fun `hd-only gif stays in the hd tier even under an sd quality pick`() {
        val g = gif(sdUrl = null)
        assertEquals("g1:hd", g.renditionCacheKey(g.hdUrl!!))
    }
}
