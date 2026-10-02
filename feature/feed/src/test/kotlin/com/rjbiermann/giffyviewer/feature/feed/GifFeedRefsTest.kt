package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.model.NicheRef
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

    @Test
    fun `gif niches become packed refs after creator before tags`() {
        val withNiches =
            gif().copy(
                niches = listOf(NicheRef("9", "Name"), NicheRef("10", "Other")),
            )
        val refs = gifFeedRefs(withNiches)
        assertEquals("creator:creatorx", refs.first())
        assertEquals("niche:9|Name", refs[1])
        assertEquals("niche:10|Other", refs[2])
        assertEquals("tag:petite", refs[3])
    }

    @Test
    fun `niche cap of three`() {
        val withNiches =
            gif().copy(
                niches = (1..5).map { NicheRef("$it", "n$it") },
            )
        val refs = gifFeedRefs(withNiches)
        assertEquals(3, refs.count { it.startsWith("niche:") })
    }

    @Test
    fun `matching niches ordered first (rule shared with both pickers)`() {
        val a = followedNiche("1", "All", tags = listOf("petite"))
        val b = followedNiche("2", "B")
        val ordered = orderNichesByTagMatch(listOf("Petite "), listOf(b, a))
        assertEquals(listOf(a, b), ordered)
    }

    private fun followedNiche(
        id: String,
        name: String?,
        tags: List<String> = emptyList(),
    ): com.rjbiermann.giffyviewer.core.network.FollowedNicheDto =
        com.rjbiermann.giffyviewer.core.network.FollowedNicheDto(
            id = id,
            name = name,
            tags = tags,
        )
}
