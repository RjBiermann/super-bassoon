package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator.MediatorResult
import com.rjbiermann.giffyviewer.core.database.FeedPageDao
import com.rjbiermann.giffyviewer.core.database.FeedPageEntity
import com.rjbiermann.giffyviewer.core.database.GifDao
import com.rjbiermann.giffyviewer.core.database.GifEntity
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.network.upstreamApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Custom feed merge rules (PLAN §7 builder): round-robin across refs with
 * stable mapping (page n → refs[(n-1) % n], inner page (n-1)/n + 1), and the
 * builder's ref round-trip.
 */
@OptIn(ExperimentalPagingApi::class)
class FeedMediatorCustomTest {
    private class FakeGifDao : GifDao {
        val upserted = mutableListOf<List<GifEntity>>()

        override suspend fun upsertAll(gifs: List<GifEntity>) = upserted.add(gifs).let { }

        override suspend fun byIds(ids: List<String>): List<GifEntity> = emptyList()

        override suspend fun byId(id: String): GifEntity? = null

        override fun byIdFlow(id: String): Flow<GifEntity?> = throw NotImplementedError()

        override fun countFlow(): Flow<Int> = throw NotImplementedError()

        override suspend fun randomUnwatched(limit: Int): List<GifEntity> = emptyList()
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
    }

    private fun state(): PagingState<Int, Gif> =
        PagingState(pages = emptyList(), anchorPosition = null, config = PagingConfig(pageSize = 20), leadingPlaceholderCount = 0)

    private fun emptyState(): PagingState<Int, Gif> = state()

    private fun mediator(
        feed: FeedSource.Custom,
        api: upstreamApi,
        pageDao: FeedPageDao,
    ) = FeedMediator(feed = feed, gifDao = FakeGifDao(), pageDao = pageDao, api = api, pageSize = 20, now = { 0L })

    @Test
    fun `refs round-robin with inner pages`() {
        val feed = FeedSource.Custom(1, "Mix", listOf("creator:alpha", "tag:amateur"))
        val api = FakeApi()
        val pageDao = FakeFeedPageDao()
        runBlocking {
            mediator(feed, api, pageDao).load(LoadType.REFRESH, state())
            mediator(feed, api, pageDao).load(LoadType.APPEND, emptyState())
        }
        // p1 → creator:alpha inner 1; p2 → tag:amateur inner 1
        assertEquals(1, api.userGifsCalls)
        assertEquals(1, api.searchCalls)
        assertEquals(listOf("amateur" to 1), api.searchArgs)
        assertEquals("custom:1:p2", pageDao.pages["custom:1:p2"]!!.pageKey)
    }

    @Test
    fun `second inner page of same ref advances inner page`() {
        val feed = FeedSource.Custom(2, "Solo", listOf("creator:alpha"))
        val api = FakeApi()
        val pageDao = FakeFeedPageDao()
        runBlocking {
            mediator(feed, api, pageDao).load(LoadType.REFRESH, state())
            mediator(feed, api, pageDao).load(LoadType.APPEND, emptyState())
        }
        assertEquals(2, api.userGifsCalls)
        assertEquals(0, api.searchCalls)
    }

    @Test
    fun `empty refs writes empty page and ends pagination`() {
        val feed = FeedSource.Custom(3, "Empty", emptyList())
        val api = FakeApi()
        val pageDao = FakeFeedPageDao()
        val result = runBlocking { mediator(feed, api, pageDao).load(LoadType.REFRESH, state()) }
        assertTrue(result is MediatorResult.Success)
        assertTrue((result as MediatorResult.Success).endOfPaginationReached)
        assertEquals(0, api.userGifsCalls)
        assertEquals(0, api.searchCalls)
    }

    @Test
    fun `niche ref fetches via nicheGifs`() {
        val feed = FeedSource.Custom(4, "Niche mix", listOf("niche:bbc|Big Black"))
        val api = FakeApi()
        val pageDao = FakeFeedPageDao()
        val result = runBlocking { mediator(feed, api, pageDao).load(LoadType.REFRESH, state()) }
        assertTrue(result is MediatorResult.Success)
        assertEquals(1, api.nicheGifsCalls)
        assertEquals(listOf("bbc" to 1), api.nicheGifsArgs)
    }

    @Test
    fun `append with poisoned state advances past highest cached page`() {
        // Fast scroll: the source's in-memory list ends on an EMPTY unfetched
        // page (lastNext=null) — Paging consults us; the OLD fallback (page 1's
        // next) re-fetched p2 forever. Regression: feed "just stops".
        val feed = FeedSource.Custom(9, "Race", listOf("creator:alpha"))
        val api = FakeApi()
        val pageDao = FakeFeedPageDao()
        // cache has p1+p2 (e.g. from an earlier session)
        runBlocking { mediator(feed, api, pageDao).load(LoadType.REFRESH, emptyState()) }
        runBlocking { mediator(feed, api, pageDao).load(LoadType.APPEND, emptyState()) }
        // poisoned: state's last page = empty, nextKey = null
        val result = runBlocking { mediator(feed, api, pageDao).load(LoadType.APPEND, emptyState()) }
        assertTrue(result is MediatorResult.Success)
        assertFalse((result as MediatorResult.Success).endOfPaginationReached)
        // p1(refresh) + p2(append1) + p3(append2) = 3 userGifs calls, NOT another p2
        assertEquals(3, api.userGifsCalls)
    }

    @Test
    fun `ref parse round-trips`() {
        assertEquals(listOf("creator:alpha", "tag:amateur"), parseCustomRefs("creator:alpha,tag:amateur"))
        assertFalse(parseCustomRefs(",,").isNotEmpty())
    }
}
