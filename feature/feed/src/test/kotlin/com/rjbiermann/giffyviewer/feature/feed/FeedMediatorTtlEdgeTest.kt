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
import com.rjbiermann.giffyviewer.core.network.GifsApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TTL boundary edges of the plan-§5 REFRESH rule beyond
 * [FeedMediatorFavoritesTest]/[FeedMediatorCustomTest]: fresh-inside-TTL skip,
 * the exact TTL boundary counts as stale, force (pull-to-refresh) bypasses a
 * fresh cache, the cached-EMPTY-page TTL bypass (issue #30) applies to every
 * feed — not just Favorites, and cache-derived APPEND advance.
 * NOT covered here: the offline-failure branches (IOException/HttpException
 * → MediatorResult.Error). [FeedMediator.runPage] logs those via a direct
 * android.util.Log call that throws on the JVM rung (no Robolectric, no
 * returnDefaultValues in the module config) — thread-logged at the
 * Robolectric/instrumented rungs, which is issue #50 stage 3/4 territory.
 */
@OptIn(ExperimentalPagingApi::class)
class FeedMediatorTtlEdgeTest {
    private class FakeGifDao : GifDao {
        override suspend fun upsertAll(gifs: List<GifEntity>) {}

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

        override suspend fun evictFavorites(prefix: String) = throw NotImplementedError()
    }

    private var virtualNow = 0L

    private fun state(): PagingState<Int, Gif> =
        PagingState(pages = emptyList(), anchorPosition = null, config = PagingConfig(pageSize = 20), leadingPlaceholderCount = 0)

    private fun mediator(
        api: GifsApi = FakeApi(),
        feed: FeedSource = FeedSource.Trending,
        pageDao: FeedPageDao = FakeFeedPageDao(),
        force: Boolean = false,
    ): FeedMediator =
        FeedMediator(
            feed = feed,
            gifDao = FakeGifDao(),
            pageDao = pageDao,
            api = api,
            pageSize = 20,
            now = { virtualNow },
            force = force,
        )

    /** Non-empty cached p1 at fetchedAt=0 (nextPageKey null = page 1 of the walk). */
    private fun TrendingCache(pageDao: FakeFeedPageDao) = seedCache(pageDao, ids = listOf("gif1"))

    private fun seedCache(
        pageDao: FakeFeedPageDao,
        ids: List<String>,
    ) {
        pageDao.pages["trend:v2:pop:p1"] = FeedPageEntity("trend:v2:pop:p1", ids, null, fetchedAt = 0)
    }

    @Test
    fun `fresh non-empty cache inside the TTL skips the network`() {
        val api = FakeApi()
        val pageDao = FakeFeedPageDao()
        seedCache(pageDao, listOf("gif1"))
        virtualNow = FeedSource.TTL_TRENDING - 1 // one ms before expiry
        val result = runBlocking { mediator(api, pageDao = pageDao).load(LoadType.REFRESH, state()) }
        assertTrue(result is MediatorResult.Success)
        assertFalse((result as MediatorResult.Success).endOfPaginationReached)
        assertEquals(0, api.trendingCalls.size)
    }

    @Test
    fun `cache exactly at the TTL boundary counts as stale and refreshes`() {
        val api = FakeApi()
        val pageDao = FakeFeedPageDao()
        seedCache(pageDao, listOf("gif1"))
        virtualNow = FeedSource.TTL_TRENDING
        val result = runBlocking { mediator(api, pageDao = pageDao).load(LoadType.REFRESH, state()) }
        assertTrue(result is MediatorResult.Success)
        assertEquals(listOf<Int>(1), api.trendingCalls)
    }

    @Test
    fun `force refresh bypasses a fresh cache`() {
        val api = FakeApi()
        val pageDao = FakeFeedPageDao()
        seedCache(pageDao, listOf("gif1"))
        virtualNow = 1 // deep inside the TTL
        val result = runBlocking { mediator(api, pageDao = pageDao, force = true).load(LoadType.REFRESH, state()) }
        assertTrue(result is MediatorResult.Success)
        assertEquals(listOf<Int>(1), api.trendingCalls)
    }

    @Test
    fun `cached EMPTY first page inside the TTL bypasses the TTL (issue #30, every feed)`() {
        val api = FakeApi()
        val pageDao = FakeFeedPageDao()
        seedCache(pageDao, emptyList())
        virtualNow = 1
        val result = runBlocking { mediator(api, pageDao = pageDao).load(LoadType.REFRESH, state()) }
        assertTrue(result is MediatorResult.Success)
        assertEquals("an empty cached first page is never fresh", listOf<Int>(1), api.trendingCalls)
    }

    @Test
    fun `no cached page at all fetches the first page`() {
        val api = FakeApi()
        val result = runBlocking { mediator(api).load(LoadType.REFRESH, state()) }
        assertTrue(result is MediatorResult.Success)
        assertEquals(listOf<Int>(1), api.trendingCalls)
    }

    @Test
    fun `append advances past the highest cached page when the state ends unfetched`() {
        val api = FakeApi()
        val pageDao = FakeFeedPageDao()
        seedCache(pageDao, listOf("gif1"))
        pageDao.pages["trend:v2:pop:p2"] = FeedPageEntity("trend:v2:pop:p2", listOf("gif2"), null, fetchedAt = 0)
        // state.pages empty (lastNext = null) → derive from the cache: p3, NOT p2 again
        val result = runBlocking { mediator(api, pageDao = pageDao).load(LoadType.APPEND, state()) }
        assertTrue(result is MediatorResult.Success)
        assertEquals(listOf(3), api.trendingCalls)
    }
}
