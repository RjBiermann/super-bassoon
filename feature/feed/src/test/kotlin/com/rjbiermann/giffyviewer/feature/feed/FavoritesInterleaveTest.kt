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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #54 favorites interleave: ONE page fill fetches the creators of its round
 * window (ceil(pageSize/window) gifs each, limiter-paced sequential
 * sub-fetches — each a counted request) and merges the chunks round-robin, so
 * a screenful alternates creators instead of showing one creator's 20-gif run.
 * Inner pages advance one per PAGE (inner = page): a (creator, inner page)
 * pair is served by exactly one page — no duplicates, no loss — so the #36
 * endless/exhaustion machinery keeps working unchanged. Every case here is
 * k <= FAVORITES_CREATORS_PER_FILL, where the window is the whole set — the
 * #62 bound leaves that mapping untouched (pinned by FavoritesFanOutTest);
 * the fan-out bound itself is FavoritesFanOutTest's contract.
 */
class FavoritesInterleaveTest {
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

    /** user → page → gif ids; records every userGifs call (user, page, count). */
    private class PoolApi : FakeApi() {
        data class Call(
            val username: String,
            val page: Int,
            val count: Int,
        )

        val calls = mutableListOf<Call>()
        val pool = mutableMapOf<Pair<String, Int>, List<String>>()

        override suspend fun userGifs(
            username: String,
            count: Int,
            page: Int,
            order: String?,
        ): GifsPageDto {
            calls.add(Call(username, page, count))
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
    fun `one fill interleaves every creator round-robin in rotation order`() =
        runBlocking {
            val api = PoolApi()
            api.pool["a" to 1] = listOf("a1", "a2", "a3", "a4", "a5")
            api.pool["b" to 1] = listOf("b1", "b2", "b3", "b4", "b5")
            api.pool["c" to 1] = listOf("c1", "c2", "c3", "c4", "c5")
            val pageDao = FakeFeedPageDao()
            val fetcher = fetcher(api, listOf("a", "b", "c"), pageDao)

            assertTrue(fetcher.fill(1))
            // Seed 0 → rotation order [a, b, c]; the shares merge round-robin,
            // gif by gif — the screenful alternates creators.
            assertEquals(
                listOf("a1", "b1", "c1", "a2", "b2", "c2", "a3", "b3", "c3", "a4", "b4", "c4", "a5", "b5", "c5"),
                pageDao.pages["fav:v4:p1"]!!.gifIds,
            )
            // One sub-fetch per creator, all at the same inner page, sharing
            // the page budget: ceil(20/3) = 7 per call (a short pool just
            // contributes what it has).
            assertEquals(listOf("a", "b", "c"), api.calls.map { it.username })
            assertTrue(api.calls.all { it.page == 1 })
            assertTrue(api.calls.all { it.count == 7 })
        }

    @Test
    fun `unequal pools interleave honestly and a dry creator drops out`() =
        runBlocking {
            val api = PoolApi()
            api.pool["a" to 1] = listOf("a1", "a2")
            api.pool["b" to 1] = listOf("b1", "b2", "b3", "b4", "b5")
            api.pool["c" to 1] = listOf("c1")
            val pageDao = FakeFeedPageDao()
            val fetcher = fetcher(api, listOf("a", "b", "c"), pageDao)

            assertTrue(fetcher.fill(1))
            assertEquals(
                listOf("a1", "b1", "c1", "a2", "b2", "b3", "b4", "b5"),
                pageDao.pages["fav:v4:p1"]!!.gifIds,
            )
        }

    @Test
    fun `inner pages advance one per page with no duplicate fetches`() =
        runBlocking {
            val api = PoolApi()
            api.pool["a" to 1] = listOf("a1", "a2", "a3")
            api.pool["a" to 2] = listOf("a4", "a5")
            api.pool["b" to 1] = listOf("b1", "b2")
            val pageDao = FakeFeedPageDao()
            val fetcher = fetcher(api, listOf("a", "b"), pageDao)

            assertTrue(fetcher.fill(1))
            assertTrue(fetcher.fill(2))
            // Page 2 fetches EVERY creator at inner 2 (b's pool 2 is dry) —
            // and each (creator, inner page) pair was fetched exactly once.
            // The lead creator rotates by page (order[(page-1+i)]) so no
            // creator always leads a screenful — #54 novelty.
            assertEquals(
                listOf("a", "b", "b", "a"),
                api.calls.map { it.username },
            )
            assertEquals(listOf(1, 1, 2, 2), api.calls.map { it.page })
            assertEquals(10, api.calls.first().count)
            val served = pageDao.pages.values.flatMapTo(HashSet()) { it.gifIds }
            assertEquals(setOf("a1", "a2", "a3", "a4", "a5", "b1", "b2"), served)
        }

    @Test
    fun `a page row is empty only when every creator's sub-fetch is empty`() =
        runBlocking {
            val api = PoolApi()
            api.pool["a" to 1] = listOf("a1")
            val pageDao = FakeFeedPageDao()
            val fetcher = fetcher(api, listOf("a", "b"), pageDao)

            // b is dry but a still feeds the row — a partially-empty fill
            // keeps chaining (the #36 mid-loop rule, now per sub-fetch).
            assertTrue(fetcher.fill(1))
            assertEquals(listOf("a1"), pageDao.pages["fav:v4:p1"]!!.gifIds)
            // Both pools dry at inner 2 → the row is empty and cached so.
            assertTrue(fetcher.fill(2))
            assertTrue(pageDao.pages["fav:v4:p2"]!!.gifIds.isEmpty())
        }

    @Test
    fun `interleaveChunks is a plain round-robin merge`() {
        assertEquals(listOf(1, 4, 2, 5, 3, 6), interleaveChunks(listOf(listOf(1, 2, 3), listOf(4, 5, 6))))
        assertEquals(listOf(1, 4, 2, 5, 6), interleaveChunks(listOf(listOf(1, 2), listOf(4, 5, 6))))
        assertEquals(listOf(1, 4, 2), interleaveChunks(listOf(listOf(1, 2), listOf(4))))
        assertEquals(emptyList<Int>(), interleaveChunks<Int>(emptyList()))
        assertEquals(emptyList<Int>(), interleaveChunks<Int>(listOf(emptyList(), emptyList())))
    }
}
