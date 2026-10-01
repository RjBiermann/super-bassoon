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
            description = null,
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

class OrientationTest {
    private fun gif(
        w: Int,
        h: Int,
    ) = Gif(
        id = "x",
        userName = "u",
        description = null,
        tags = emptyList(),
        likes = 0,
        views = 0,
        durationSeconds = 1.0,
        hasAudio = false,
        width = w,
        height = h,
        createDateEpoch = 0,
        published = true,
        avgColor = "#000000",
        sdUrl = null,
        hdUrl = null,
        posterUrl = null,
        niches = emptyList(),
    )

    @Test
    fun `orientation predicate`() {
        assert(gif(1080, 1920).matchesOrientation("vertical"))
        assert(gif(1920, 1080).matchesOrientation("horizontal"))
        assert(!gif(1920, 1080).matchesOrientation("vertical"))
        assert(!gif(1080, 1920).matchesOrientation("horizontal"))
        // any passes all
        assert(gif(1080, 1920).matchesOrientation("any"))
        assert(gif(1920, 1080).matchesOrientation("any"))
        // unknown dims pass through (TV card-width fallback rule)
        assert(gif(0, 0).matchesOrientation("vertical"))
        assert(gif(0, 0).matchesOrientation("horizontal"))
    }
}
