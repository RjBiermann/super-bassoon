package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.database.FeedPageDao
import com.rjbiermann.giffyviewer.core.database.FeedPageEntity
import com.rjbiermann.giffyviewer.core.database.GifDao
import com.rjbiermann.giffyviewer.core.database.GifEntity
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.dto.GifsPageDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #5 "infinite until nothing left": a Favorites page that comes back empty EARLY
 * in a rotation must NOT end the feed — the next rotation's slots still have
 * pages (the old behavior ended the feed on the FIRST empty creator slot). An
 * empty LAST slot of a rotation ends it (bounded: one API call per fetch, the
 * next rotation's first slot is not probed).
 */
class FavoritesRoundRobinExhaustionTest {
    private class FakeGifDao : GifDao {
        override suspend fun upsertAll(gifs: List<GifEntity>) = Unit

        override suspend fun byIds(ids: List<String>): List<GifEntity> = emptyList()

        override suspend fun byId(id: String): GifEntity? = null

        override fun byIdFlow(id: String): Flow<GifEntity?> = throw NotImplementedError()

        override fun countFlow(): Flow<Int> = throw NotImplementedError()
    }

    private class FakeFeedPageDao : FeedPageDao {
        val pages = HashMap<String, FeedPageEntity>()

        override suspend fun pagesForBase(keyBase: String): List<FeedPageEntity> = pages.values.filter { it.pageKey.startsWith(keyBase) }

        override suspend fun upsert(page: FeedPageEntity) {
            pages[page.pageKey] = page
        }

        override suspend fun page(pageKey: String): FeedPageEntity? = pages[pageKey]

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

    /** user+page → gif ids; a missing entry or empty list = the creator's pool end. */
    private class PoolApi : FakeApi() {
        val calls = mutableListOf<Pair<String, Int>>()
        val pool = mutableMapOf<Pair<String, Int>, List<String>>()

        override suspend fun userGifs(
            username: String,
            count: Int,
            page: Int,
            order: String,
        ): GifsPageDto {
            calls.add(username to page)
            return GifsPageDto(gifs = (pool[username to page] ?: emptyList()).map { gifDtoShell(it, username) })
        }
    }

    private fun fetcher(
        api: GifsApi,
        creators: List<String>,
        pageDao: FeedPageDao,
    ) = FeedPageFetcher(
        FeedSource.Favorites,
        FakeGifDao(),
        pageDao,
        api,
        20,
        favorites = { creators },
    )

    @Test
    fun `empty slot mid-rotation chains forward and the feed ends at an empty last rotation slot`() =
        runBlocking {
            val api = PoolApi()
            api.pool["a" to 1] = listOf("a1")
            api.pool["c" to 1] = listOf("c1")
            api.pool["a" to 2] = listOf("a2")
            val pageDao = FakeFeedPageDao()
            val fetcher = fetcher(api, listOf("a", "b", "c"), pageDao)

            // Rotation 1: fill() returns MORE (true = keep going) even when the
            // page itself came back empty — b's slot is not the feed's end.
            assertTrue(fetcher.fill(1))
            assertTrue("b's empty slot must not end the feed (2 of 3 slots left)", fetcher.fill(2))
            assertTrue(fetcher.fill(3))
            // Rotation 2: a2 (content), b empty (mid → continue), c empty at the
            // rotation boundary (page 6 % 3 == 0) → the honest end.
            assertTrue(fetcher.fill(4))
            assertTrue(fetcher.fill(5))
            assertFalse("the empty LAST slot of a rotation ends the feed", fetcher.fill(6))

            // Mid-rotation empty rows chain to the next page (the source's walk
            // keeps hopping); the terminal rotation-boundary row does not.
            assertEquals("fav:v1:p3", pageDao.pages["fav:v1:p2"]!!.nextPageKey)
            assertEquals("fav:v1:p6", pageDao.pages["fav:v1:p5"]!!.nextPageKey)
            assertNull(pageDao.pages["fav:v1:p6"]!!.nextPageKey)
        }

    @Test
    fun `single creator keeps today's end semantics (every empty page is terminal)`() =
        runBlocking {
            val api = PoolApi()
            api.pool["a" to 1] = listOf("a1")
            val pageDao = FakeFeedPageDao()
            val fetcher = fetcher(api, listOf("a"), pageDao)
            assertTrue(fetcher.fill(1))
            assertFalse(fetcher.fill(2))
            assertNull(pageDao.pages["fav:v1:p2"]!!.nextPageKey)
        }

    @Test
    fun `rotation preserves the round-robin inner-page math`() {
        assertEquals(listOf("a", "b"), rotateCreators(listOf("a", "b"), 0L))
        assertEquals(listOf("b", "a"), rotateCreators(listOf("a", "b"), 1L))
        assertEquals(listOf("c", "a", "b"), rotateCreators(listOf("a", "b", "c"), 2L))
        // Negative seeds are modulo-safe: same bucket as the positive twin.
        assertEquals(rotateCreators(listOf("a", "b", "c"), 1L), rotateCreators(listOf("a", "b", "c"), -2L))
        assertEquals(emptyList<String>(), rotateCreators(emptyList(), 5L))
    }
}
