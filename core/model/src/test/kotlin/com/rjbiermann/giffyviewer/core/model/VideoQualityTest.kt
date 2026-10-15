package com.rjbiermann.giffyviewer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** §5 preferred-quality rule (issue #4, LIVE-PROBE-CLOSED single-rendition
 *  branch): the pref only picks the STREAM URL — no rendition ladder exists
 *  (urls.hd/urls.sd are both plain MP4s), so there is no track-cap machinery.
 *  Precedence pinned: dataSaver first, then the discrete SD choice (it must
 *  NOT depend on dataSaver state), then today's auto/highest default. */
class VideoQualityTest {
    @Test
    fun `value set is the one menu list - auto default first`() {
        assertEquals(listOf("auto", "highest", "sd"), VideoQuality.VALUES)
        assertEquals(VideoQuality.AUTO, VideoQuality.VALUES.first())
    }

    @Test
    fun `saver always picks sd - independent of the quality pref`() {
        val gif = gif(sd = "sd.mp4", hd = "hd.mp4")
        assertEquals("sd.mp4", gif.qualityStreamUrl(dataSaver = true, quality = VideoQuality.AUTO))
        assertEquals("sd.mp4", gif.qualityStreamUrl(dataSaver = true, quality = VideoQuality.HIGHEST))
        assertEquals("sd.mp4", gif.qualityStreamUrl(dataSaver = true, quality = VideoQuality.SD))
        // Saver fallback: no sd stream on the gif → hd.
        assertEquals("hd.mp4", gif(sd = null, hd = "hd.mp4").qualityStreamUrl(dataSaver = true, quality = VideoQuality.SD))
    }

    @Test
    fun `sd choice prefers the sd stream regardless of dataSaver`() {
        val gif = gif(sd = "sd.mp4", hd = "hd.mp4")
        assertEquals("sd.mp4", gif.qualityStreamUrl(dataSaver = false, quality = VideoQuality.SD))
        // auto/highest keep today's default (hd first).
        assertEquals("hd.mp4", gif.qualityStreamUrl(dataSaver = false, quality = VideoQuality.AUTO))
        assertEquals("hd.mp4", gif.qualityStreamUrl(dataSaver = false, quality = VideoQuality.HIGHEST))
    }

    @Test
    fun `missing rendition falls back to the other stream`() {
        val gif = gif(sd = null, hd = "hd.mp4")
        assertEquals("hd.mp4", gif.qualityStreamUrl(dataSaver = false, quality = VideoQuality.SD))
        val sdOnly = gif(sd = "sd.mp4", hd = null)
        assertEquals("sd.mp4", sdOnly.qualityStreamUrl(dataSaver = false, quality = VideoQuality.AUTO))
        assertEquals("sd.mp4", sdOnly.qualityStreamUrl(dataSaver = false, quality = VideoQuality.HIGHEST))
    }

    private fun gif(
        sd: String?,
        hd: String?,
    ): Gif =
        Gif(
            id = "g1",
            userName = "u",
            description = null,
            tags = emptyList(),
            likes = 0,
            views = 0,
            durationSeconds = 1.0,
            hasAudio = false,
            width = 100,
            height = 100,
            createDateEpoch = 0,
            published = true,
            avgColor = "#000000",
            sdUrl = sd,
            hdUrl = hd,
            posterUrl = null,
            niches = emptyList(),
        )
}
