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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #36 endless-favorites contract (2026-10 loop-reshuffle policy): the pager
 * keeps serving pages while the pool still has UNFETCHED content — an empty
 * slot (mid- or boundary) never ends the feed on its own; the feed ends one
 * dead loop after the FULL previous loop came back empty (the bounded
 * exhaustion cost), never while any unfetched inner page exists. Empty never
 * becomes an infinite probe storm: the pre-check skips the probe of a loop
 * whose predecessor was entirely empty. Loop 0 keeps the #5 rotation;
 * every later loop re-shuffles its creator order with a NEW derived seed
 * (favoritesLoopOrder) so the served creator-grouping sequence never repeats.
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
        seed: Long = 0L,
    ) = FeedPageFetcher(
        FeedSource.Favorites,
        FakeGifDao(),
        pageDao,
        api,
        20,
        favorites = { creators },
        favoritesSeed = { seed },
    )

    @Test
    fun `the pager serves every unfetched pool page and ends exactly one dead loop after the pool spends`() =
        runBlocking {
            val api = PoolApi()
            // Pools: a has 3 inner pages, b has none, c has 1. Unequal pools are
            // the old bug's trigger: the boundary slot's creator (c, exhausted
            // after its page 1) used to END the feed while a's pages 2..3 were
            // still unfetched.
            api.pool["a" to 1] = listOf("a1")
            api.pool["a" to 2] = listOf("a2")
            api.pool["a" to 3] = listOf("a3")
            api.pool["c" to 1] = listOf("c1")
            val pageDao = FakeFeedPageDao()
            val fetcher = fetcher(api, listOf("a", "b", "c"), pageDao, seed = 7L)

            var page = 1
            while (fetcher.fill(page)) {
                page++
                check(page < 40) { "the endless-favorites loop never ended (bounded exhaustion violated)" }
            }
            // Honest end, not an error: the NEXT page skips its probe entirely
            // (the pre-check marks the full previous loop empty).
            assertFalse("an empty pool must end the pager", fetcher.fill(page))
            val callsAfterEnd = api.calls.size
            assertFalse(fetcher.fill(page))
            assertEquals("an ended pager must not re-probe", callsAfterEnd, api.calls.size)

            // No content was lost: ALL four pool gif pages reached a row. A
            // reshuffled loop still visits every creator exactly once per loop,
            // so unfetched content is reachable regardless of the loop order.
            val served = pageDao.pages.values.flatMapTo(HashSet()) { it.gifIds }
            assertEquals(setOf("a1", "a2", "a3", "c1"), served)

            // Loop 0 kept the #5 rotation contract: seed 7 rotates [a,b,c] →
            // [b,c,a] (offset 7 % 3 = 1). One API call per fill — the rate-limit
            // invariant rides on the fetch count per page, unchanged.
            assertEquals(listOf("b", "c", "a"), api.calls.take(3).map { it.first })
            assertEquals("an early empty slot must still chain forward", "fav:v2:p2", pageDao.pages["fav:v2:p1"]!!.nextPageKey)
        }

    @Test
    fun `single creator serves every inner page, then one dead page, then ends`() =
        runBlocking {
            val api = PoolApi()
            api.pool["a" to 1] = listOf("a1")
            api.pool["a" to 2] = listOf("a2")
            api.pool["a" to 3] = listOf("a3")
            val pageDao = FakeFeedPageDao()
            val fetcher = fetcher(api, listOf("a"), pageDao, seed = 5L)

            assertTrue(fetcher.fill(1))
            assertTrue(fetcher.fill(2))
            assertTrue(fetcher.fill(3))
            // The creator's pool ran dry: the slot right after the last content
            // page MUST be probed (its previous loop page had content), then the
            // next one is skipped via the fully-empty-previous-loop check.
            assertTrue("one dead probe page is the bounded exhaustion cost", fetcher.fill(4))
            assertFalse(fetcher.fill(5))
            assertEquals(setOf("a1", "a2", "a3"), pageDao.pages.values.flatMapTo(HashSet()) { it.gifIds })
            assertEquals(4, api.calls.size)
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
