package com.rjbiermann.giffyviewer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** SLICE-14: aspect seed from the gif's own metadata (player content frame). */
class ContentAspectRatioTest {
    private fun gif(
        w: Int,
        h: Int,
    ): Gif =
        Gif(
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
    fun `width over height`() {
        assertEquals(2f, gif(200, 100).contentAspectRatio(), 0.0001f)
        assertEquals(0.5f, gif(100, 200).contentAspectRatio(), 0.0001f)
    }

    @Test
    fun `unknown dimensions seed zero (clear, not stale)`() {
        assertEquals(0f, gif(0, 0).contentAspectRatio(), 0.0001f)
        assertEquals(0f, gif(100, 0).contentAspectRatio(), 0.0001f)
        assertEquals(0f, gif(0, 100).contentAspectRatio(), 0.0001f)
    }
}
