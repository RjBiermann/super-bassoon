package com.rjbiermann.giffyviewer.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #65 cache generation (the #59/#62 lesson — "mapping changed, keys didn't"):
 * the creator feed's page→content mapping changed (pages are now fetched with
 * [CREATOR_DEFAULT_ORDER] `latest` instead of `trending`) and every Favorites
 * page embeds creator sub-fetches of that same endpoint, so both cache bases
 * carry the generation. Rows written by the old mapping must stay out of the
 * walk's reach while the DataStore prefs identity (sort/filter/seed) does NOT
 * follow the bump — otherwise an upgrade silently resets user prefs.
 */
class CreatorMappingKeyTest {
    @Test
    fun `the creator cache base carries the mapping generation`() {
        assertEquals("user:v2:perkyandpink", FeedSource.Creator("perkyandpink").keyBase)
        assertEquals("user:v2:perkyandpink:p1", feedPageKey(FeedSource.Creator("perkyandpink"), 1))
    }

    @Test
    fun `the sort suffix still appends after the generation token`() {
        assertEquals("user:v2:perkyandpink:sort=oldest", FeedSource.Creator("PerkyAndPink", "oldest").keyBase)
        assertEquals("user:v2:perkyandpink:sort=oldest:p2", feedPageKey(FeedSource.Creator("perkyandpink", "oldest"), 2))
    }

    @Test
    fun `the creator prefs identity stays stable across generations`() {
        // baseKey = the DataStore per-feed prefs key (sort/filters): the
        // generation bump must not move it.
        assertEquals("user:perkyandpink", FeedSource.Creator("perkyandpink").baseKey)
        assertEquals("user:perkyandpink", FeedSource.Creator("perkyandpink", "oldest").baseKey)
    }

    @Test
    fun `the favorites cache base carries the new generation too`() {
        // Favorites pages embed creator sub-fetches, so the #65 order change
        // moves their content as well.
        assertEquals("fav:v4", FeedSource.Favorites.keyBase)
        assertEquals("fav:v1", FeedSource.Favorites.baseKey)
    }
}
