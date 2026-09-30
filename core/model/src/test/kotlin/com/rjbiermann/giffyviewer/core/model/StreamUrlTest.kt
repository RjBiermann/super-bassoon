package com.rjbiermann.giffyviewer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Data-saver contract (PLAN §5): ON prefers SD, OFF prefers HD; either
 * falls back to the other tier rather than failing playback.
 */
class StreamUrlTest {
    private val gif =
        Gif(
            id = "abc",
            userName = "u",
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
            sdUrl = "https://sd",
            hdUrl = "https://hd",
            posterUrl = null,
            niches = emptyList(),
        )

    @Test
    fun `data saver prefers SD`() {
        assertEquals("https://sd", gif.streamUrl(dataSaver = true))
    }

    @Test
    fun `default prefers HD`() {
        assertEquals("https://hd", gif.streamUrl(dataSaver = false))
    }

    @Test
    fun `falls back when preferred tier missing`() {
        assertEquals("https://hd", gif.copy(sdUrl = null).streamUrl(dataSaver = true))
        assertEquals("https://sd", gif.copy(hdUrl = null).streamUrl(dataSaver = false))
    }

    @Test
    fun `null when nothing available`() {
        assertEquals(null, gif.copy(sdUrl = null, hdUrl = null).streamUrl(dataSaver = false))
    }
}
