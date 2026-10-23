package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.database.FAVORITES_PAGE_PREFIX
import com.rjbiermann.giffyviewer.core.database.FeedPageDao
import com.rjbiermann.giffyviewer.core.database.FeedPageEntity
import com.rjbiermann.giffyviewer.core.database.GifEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #62 cache mapping generation (the #59 lesson: "mapping changed, keys
 * didn't"): the page→content mapping changed twice in #62 — v2 for the
 * per-creator fan-out, v3 for the window-independent slices — and each change
 * moved the Favorites page keys to its own generation ("fav:v3"). A row
 * written by an older mapping inside the last 10 min would otherwise still be
 * served by the walk and end the feed at the old tail.
 *
 * Contract pinned: the base carries the generation, the eviction prefix stays
 * GENERATION-AGNOSTIC so one favorites edit/refresh sweeps every generation's
 * rows, and the DataStore per-feed prefs identity does NOT follow the
 * generation (a bump must never reset the user's filters/sort/seed).
 */
class FavoritesMappingKeyTest {
    /** FeedPageDao fake mirroring Room's `DELETE ... WHERE pageKey LIKE :prefix || '%'`. */
    private class PrefixEvictPageDao : FeedPageDao {
        val pages = HashMap<String, FeedPageEntity>()

        override suspend fun upsert(page: FeedPageEntity) {
            pages[page.pageKey] = page
        }

        override suspend fun page(pageKey: String): FeedPageEntity? = pages[pageKey]

        override suspend fun pagesForBase(keyBase: String): List<FeedPageEntity> = pages.values.filter { it.pageKey.startsWith(keyBase) }

        override fun pageFlow(pageKey: String): Flow<FeedPageEntity?> = throw NotImplementedError()

        override suspend fun gifsByIds(ids: List<String>): List<GifEntity> = emptyList()

        override suspend fun evictStale(olderThan: Long) = throw NotImplementedError()

        override suspend fun evictBase(base: String) = throw NotImplementedError()

        override suspend fun evictFavorites(prefix: String) =
            pages.keys
                .filter { it.startsWith(prefix) }
                .forEach { pages.remove(it) }
                .let { }
    }

    @Test
    fun `the favorites cache base carries the mapping generation`() {
        // The #62 slice mapping is a NEW generation: rows of the older
        // mappings stay out of the walk's reach (the eviction prefix still
        // sweeps them).
        assertEquals("fav:v3", FeedSource.Favorites.keyBase)
        assertTrue(feedPageKey(FeedSource.Favorites, 1).startsWith("fav:v3:p"))
        assertTrue(feedPageKey(FeedSource.Favorites, 1).startsWith(FAVORITES_PAGE_PREFIX))
    }

    @Test
    fun `favorites prefs identity stays stable across cache generations`() {
        // baseKey is the DataStore per-feed prefs key (FeedScreen/settings
        // feedPrefs/feedSort): the #62 generation bump must not move it, or
        // every upgrade silently resets Favorites filters/sort/shuffle seed.
        assertEquals("fav:v1", FeedSource.Favorites.baseKey)
        assertEquals("fav:v3", FeedSource.Favorites.keyBase)
    }

    @Test
    fun `favorites eviction sweeps every mapping generation`() =
        runBlocking {
            // Generation-agnostic prefix: it stops before the generation slot
            // ("fav:v" + digit + ":p"), never the versioned base.
            assertEquals("fav:v", FAVORITES_PAGE_PREFIX)
            assertTrue(feedPageKey(FeedSource.Favorites, 1).startsWith(FAVORITES_PAGE_PREFIX))
            assertTrue("fav:v1:p1".startsWith(FAVORITES_PAGE_PREFIX))

            val pageDao = PrefixEvictPageDao()
            val old = listOf("fav:v1:p1", "fav:v2:p7")
            val current = listOf("fav:v3:p1", "fav:v3:p2")
            val other = "trend:v2:pop:p1"
            for (key in old + current + listOf(other)) {
                pageDao.upsert(FeedPageEntity(pageKey = key, gifIds = listOf("g"), nextPageKey = null, fetchedAt = 0L))
            }

            pageDao.evictFavorites()

            assertEquals("one favorites edit sweeps old and current rows", setOf(other), pageDao.pages.keys)
        }
}
