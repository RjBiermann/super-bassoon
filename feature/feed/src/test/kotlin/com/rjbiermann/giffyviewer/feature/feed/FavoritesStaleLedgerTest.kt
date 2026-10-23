package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.database.FeedPageDao
import com.rjbiermann.giffyviewer.core.database.FeedPageEntity
import com.rjbiermann.giffyviewer.core.database.GifDao
import com.rjbiermann.giffyviewer.core.database.GifEntity
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.dto.GifsPageDto
import com.rjbiermann.giffyviewer.core.network.dto.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #59 regression (feed doesn't load endlessly anymore — after #54): the #54
 * interleave changed the Favorites page→content mapping (page n now fetches
 * EVERY favorited creator at inner page n; pre-#54 a page was ONE creator at
 * inner (n-1)/size+1), but the feed_pages rows kept the SAME keys under BOTH
 * mappings ("fav:v1:p<n>", keyBase untouched) and nothing ever ages them out
 * (evictStale has no caller; Favorites rows only leave via a forced REFRESH).
 * A ledger built under the old mapping (the v0.7.0→v0.7.1 app update, or any
 * favorites-set shift the UI-side evictions didn't cover) is therefore served
 * by the walk verbatim, the first fresh probe lands PAST every creator's pool
 * (inner = page number), and `previousRotationExhausted` ends the feed at the
 * old depth while unfetched pool pages remain — the reported dead end.
 *
 * Contract pinned: the walk's page acquisition step (walkFill) must
 * REVALIDATE a Favorites row past the feed's TTL instead of serving it,
 * while a fresh row still serves from cache (stale-while-revalidate within
 * the TTL; zero extra requests when nothing is stale).
 */
class FavoritesStaleLedgerTest {
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

    /** user+page → gif ids (one sub-fetch per creator per page); records calls. */
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

    /** A v0.7.0-era Favorites ledger: each page was ONE creator's 10-gif run
     *  and the walk ended there (the tail row's nextPageKey is null, like the
     *  old exhausted conclusion writes it). fetchedAt = 0 = long past the
     *  feed's TTL. */
    private fun staleOldMappingLedger(pageDao: FakeFeedPageDao) {
        val idsByPage =
            mapOf(
                1 to (1..10).map { "old_$it" },
                2 to (1..10).map { "old2_$it" },
                3 to emptyList(),
            )
        for (page in 1..3) {
            pageDao.pages[feedPageKey(FeedSource.Favorites, page)] =
                FeedPageEntity(
                    pageKey = feedPageKey(FeedSource.Favorites, page),
                    gifIds = idsByPage.getValue(page),
                    nextPageKey = if (page < 3) feedPageKey(FeedSource.Favorites, page + 1) else null,
                    fetchedAt = 0L,
                )
        }
    }

    private fun fetcher(
        api: GifsApi,
        pageDao: FeedPageDao,
    ) = FeedPageFetcher(
        FeedSource.Favorites,
        FakeGifDao(),
        pageDao,
        api,
        20,
        favorites = { listOf("a", "b") },
        favoritesSeed = { 0L },
    )

    /** The pager's read side: successive loads through the walk, exactly the
     *  shape walkFill serves (the walk stops at the first non-empty page, the
     *  next load continues at the carried key). */
    private suspend fun walkToTheEnd(
        pageDao: FakeFeedPageDao,
        api: PoolApi,
    ): Map<Int, String> {
        val loads = mutableMapOf<Int, String>()
        var page: Int? = 1
        var hops = 0
        while (page != null) {
            val walked =
                walkFeedPages(
                    startPage = page,
                    maxHops = MAX_WALK_HOPS,
                    pageFor = { walkFill(FeedSource.Favorites, pageDao, fetcher(api, pageDao), it) },
                    nextKeyOf = { entity -> entity?.nextPageKey?.let { pageNumber(it) } },
                    gifsOf = { _, entity -> entity?.gifIds?.map { gifDtoShell(it, "a").toModel() } ?: emptyList() },
                )
            if (walked.gifs.isNotEmpty()) loads[page] = walked.gifs.joinToString(",") { it.id }
            check(++hops < 20) { "walk did not terminate" }
            page = walked.nextKey
        }
        return loads
    }

    @Test
    fun `a stale favorites ledger is revalidated and the walk still reaches every pool page`() =
        runBlocking {
            val api = PoolApi()
            // Live pools under the CURRENT interleave mapping (pageSize 20,
            // 2 creators → perCreator = ceil(20/2) = 10): a has 3 inner pages,
            // b has 1. None of a2..a3 was ever served by the old ledger.
            api.pool["a" to 1] = (1..10).map { "a$it" }
            api.pool["a" to 2] = (11..20).map { "a$it" }
            api.pool["a" to 3] = (21..30).map { "a$it" }
            api.pool["b" to 1] = (1..10).map { "b$it" }
            val pageDao = FakeFeedPageDao()
            staleOldMappingLedger(pageDao)

            val loads = walkToTheEnd(pageDao, api)

            // Every live pool gif reached a row — the dead ledger is refilled,
            // not served (no old_* id survives the refill).
            val served = pageDao.pages.values.flatMapTo(HashSet()) { it.gifIds }
            assertEquals(api.pool.values.flatten().toSet(), served)
            assertFalse("stale-ledger content must not survive the refill", served.any { it.startsWith("old") })
            // The refill is the interleave: ONE sub-fetch per creator per
            // inner page (the loop orders shuffle per loop — pinned exactly by
            // FavoritesLoopReshuffleTest — so the page-level pair stays {a,b}).
            assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 5), api.calls.map { it.second })
            api.calls.groupBy { it.second }.forEach { (page, pageCalls) ->
                assertEquals("page $page: one sub-fetch per creator", setOf("a", "b"), pageCalls.map { it.first }.toSet())
            }
            // Consecutive loads kept producing content until the pools ran
            // dry — the "loads endlessly while pools have videos" behavior.
            assertTrue("pages 1..3 all served content", loads.keys.containsAll(listOf(1, 2, 3)))
            // The rebuilt tail ends the bounded-exhaustion way: dead pages
            // are cached empty and CHAINED (the #36 mid-loop rule) for one
            // dead window; the next probe is pre-skipped and the walk
            // terminates with a null key, no error, no extra fetch.
            val tail = pageDao.pages[feedPageKey(FeedSource.Favorites, 4)]!!
            assertTrue(tail.gifIds.isEmpty())
            assertEquals(feedPageKey(FeedSource.Favorites, 5), tail.nextPageKey)
            assertTrue(pageDao.pages[feedPageKey(FeedSource.Favorites, 5)]!!.gifIds.isEmpty())
            assertNull(pageDao.pages[feedPageKey(FeedSource.Favorites, 6)])
        }

    @Test
    fun `a fresh favorites row still serves from cache (stale-while-revalidate within ttl)`() =
        runBlocking {
            val api = PoolApi()
            val pageDao = FakeFeedPageDao()
            pageDao.pages[feedPageKey(FeedSource.Favorites, 1)] =
                FeedPageEntity(
                    pageKey = feedPageKey(FeedSource.Favorites, 1),
                    gifIds = listOf("fresh1", "fresh2"),
                    nextPageKey = feedPageKey(FeedSource.Favorites, 2),
                    fetchedAt = System.currentTimeMillis(),
                )

            val entity = walkFill(FeedSource.Favorites, pageDao, fetcher(api, pageDao), 1)

            // Fresh row: served as-is, zero sub-fetches (rate-limit invariant).
            assertEquals(listOf("fresh1", "fresh2"), entity!!.gifIds)
            assertTrue(api.calls.isEmpty())
        }
}
