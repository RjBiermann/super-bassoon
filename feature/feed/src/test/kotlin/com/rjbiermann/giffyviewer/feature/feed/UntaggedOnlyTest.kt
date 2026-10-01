package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.datastore.FeedPrefs
import com.rjbiermann.giffyviewer.core.model.Gif
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** §8 strict-tags (group feeds): non-empty + all tags inside the bundle. */
class UntaggedOnlyTest {
    private fun gif(tags: List<String>) =
        Gif(
            id = "x",
            userName = "u",
            description = null,
            tags = tags,
            likes = 0,
            views = 0,
            durationSeconds = 5.0,
            hasAudio = false,
            width = 1,
            height = 1,
            createDateEpoch = 0,
            published = true,
            avgColor = "",
            sdUrl = null,
            hdUrl = null,
            posterUrl = null,
            niches = emptyList(),
        )

    private fun group(tags: List<String>) = FeedSource.Group(id = 1, name = "g", tags = tags)

    private val prefs = FeedPrefs(untaggedOnly = true)

    @Test
    fun `empty-tag gif is dropped`() {
        assertFalse(untagged(gif(emptyList()), group(listOf("MILF")), prefs))
    }

    @Test
    fun `outside-tag gif is dropped`() {
        assertFalse(untagged(gif(listOf("MILF", "Extra")), group(listOf("MILF")), prefs))
    }

    @Test
    fun `in-bundle gif passes`() {
        assertTrue(untagged(gif(listOf("milf")), group(listOf("MILF")), prefs))
    }
}
