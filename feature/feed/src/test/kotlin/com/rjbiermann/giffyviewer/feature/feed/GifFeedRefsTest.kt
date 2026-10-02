package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.model.Gif
import org.junit.Assert.assertEquals
import org.junit.Test

/** Shared picker ref-building (mobile AddToCustomFeedDialog + TV TvQuickActions). */
class GifFeedRefsTest {
    private fun gif(
        user: String = " CreatorX ",
        tags: List<String> = listOf(" Petite ", "shower", "Brunette", "fourth"),
    ) = Gif(
        id = "g1",
        description = null,
        userName = user,
        tags = tags,
        likes = 0,
        views = 0,
        durationSeconds = 5.0,
        hasAudio = false,
        width = 720,
        height = 1280,
        createDateEpoch = 0L,
        published = true,
        avgColor = "#000000",
        sdUrl = null,
        hdUrl = null,
        posterUrl = null,
        niches = emptyList(),
        verified = true,
    )

    @Test
    fun `creator first with tag cap of three`() {
        val refs = gifFeedRefs(gif())
        assertEquals(listOf("creator:creatorx", "tag:petite", "tag:shower", "tag:brunette"), refs)
    }

    @Test
    fun `niche ref prepended when given`() {
        val refs = gifFeedRefs(gif(user = "a"), nicheRef = "niche:9|Name")
        assertEquals("niche:9|Name", refs.first())
        assertEquals("creator:a", refs[1])
    }
}
