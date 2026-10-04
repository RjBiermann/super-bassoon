package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.database.FAVORITES_PAGE_PREFIX
import com.rjbiermann.giffyviewer.core.database.FeedPageDao
import com.rjbiermann.giffyviewer.core.database.FeedPageEntity
import com.rjbiermann.giffyviewer.core.database.GifDao
import com.rjbiermann.giffyviewer.core.database.GifEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Favorites-cache invalidation (2026-10 user report: favoriting a creator
 * blanked the Favorites feed until the 10-min TTL — the cached round-robin
 * pages were built against the OLD favorite set, so the read-time favorites
 * filter dropped every row). Two contracts pinned:
 * 1. the eviction pattern in :core:database matches the Favorites source keys
 *    (a keyBase rename without the DAO update would silently never evict);
 * 2. after eviction the next load refetches through the fetcher against the
 *    NEW favorite set (self-fill path, no background job).
 */
class FavoritesCacheInvalidationTest {
    /** Contract pin: FAVORITES_PAGE_PREFIX ↔ FeedSource.Favorites keys. */
    @Test
    fun `favorites eviction pattern matches the favorites source page keys`() {
        assertEquals("fav:v1", FeedSource.Favorites.keyBase)
        assertEquals("fav:v1:p", FAVORITES_PAGE_PREFIX)
        assertTrue(feedPageKey(FeedSource.Favorites, 1).startsWith(FAVORITES_PAGE_PREFIX))
        assertTrue(feedPageKey(FeedSource.Favorites, 12).startsWith(FAVORITES_PAGE_PREFIX))
    }

    /** The first favorite (empty pool → one creator) fills page 1, and after
     *  a favorites-set change + eviction the next fill round-robins the NEW
     *  set — the exact sequence the toggle path's invalidation relies on. */
    @Test
    fun `refetch after favorites-set change and eviction maps page 1 to the new set`() =
        runBlocking {
            val pageDao = EvictRecordingPageDao()
            val api = FakeApi()
            var favorites: List<String> = emptyList()
            val fetcher =
                FeedPageFetcher(FeedSource.Favorites, RecorderGifDao(), pageDao, api, 20, favorites = { favorites })

            // Empty pool: the fetcher fills nothing (empty-set guard, no page row).
            favorites = emptyList()
            assertFalse(fetcher.fill(1))
            assertTrue(pageDao.pages.isEmpty())

            // First favorite → the same fill now fetches that creator.
            favorites = listOf("old.creator")
            assertTrue(fetcher.fill(1))
            assertEquals(listOf("old.creator"), RecorderGifDao.upsertedUsernames)

            // The set changes again and the toggle path evicts (the fix).
            favorites = listOf("new.creator")
            pageDao.evictFavorites()
            assertNull(pageDao.pages[feedPageKey(FeedSource.Favorites, 1)])

            // Next load refetches — page 1 now round-robins the NEW creator.
            assertTrue(fetcher.fill(1))
            assertEquals(listOf("new.creator"), RecorderGifDao.upsertedUsernames)
        }

    private class RecorderGifDao : GifDao {
        companion object {
            /** Username of the last-fetch batch (FakeApi names gifs "g1"). */
            val upsertedUsernames = mutableListOf<String>()
        }

        override suspend fun upsertAll(gifs: List<GifEntity>) {
            RecorderGifDao.upsertedUsernames.clear()
            RecorderGifDao.upsertedUsernames.addAll(gifs.map { it.userName })
        }

        override suspend fun byIds(ids: List<String>): List<GifEntity> = emptyList()

        override suspend fun byId(id: String): GifEntity? = null

        override fun byIdFlow(id: String): Flow<GifEntity?> = throw NotImplementedError()

        override fun countFlow(): Flow<Int> = throw NotImplementedError()

        override suspend fun randomUnwatched(limit: Int): List<GifEntity> = emptyList()
    }

    /** FeedPageDao fake that actually implements evictFavorites (removes the
     *  preferred-prefix pages) — mirrors Room's LIKE deletion. */
    private class EvictRecordingPageDao : FeedPageDao {
        val pages = HashMap<String, FeedPageEntity>()

        override suspend fun pagesForBase(keyBase: String): List<FeedPageEntity> = pages.values.filter { it.pageKey.startsWith(keyBase) }

        override suspend fun upsert(page: FeedPageEntity) {
            pages[page.pageKey] = page
        }

        override suspend fun page(pageKey: String): FeedPageEntity? = pages[pageKey]

        override fun pageFlow(pageKey: String): Flow<FeedPageEntity?> = throw NotImplementedError()

        override suspend fun gifsByIds(ids: List<String>): List<GifEntity> = emptyList()

        override suspend fun evictStale(olderThan: Long) = throw NotImplementedError()

        override suspend fun evictBase(base: String) =
            pages.keys
                .filter { it.startsWith("$base:p") }
                .forEach { pages.remove(it) }
                .let { }

        override suspend fun evictFavorites(prefix: String) =
            pages.keys
                .filter { it.startsWith(prefix) }
                .forEach { pages.remove(it) }
                .let { }
    }
}
