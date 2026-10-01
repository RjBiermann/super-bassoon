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
import com.rjbiermann.giffyviewer.core.network.dto.GifDtoShell
import com.rjbiermann.giffyviewer.core.network.dto.UrlsDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Favorites cache rules: a cached EMPTY first page never counts as fresh, and
 * "nothing favorited" must not write an empty page row. Regression for the TV
 * Favorites row staying empty after the first favorite.
 */
@OptIn(ExperimentalPagingApi::class)
class FeedMediatorFavoritesTest {
    private class FakeGifDao : GifDao {
        val upserted = mutableListOf<List<GifEntity>>()

        override suspend fun upsertAll(gifs: List<GifEntity>) = upserted.add(gifs).let { }

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
    }

    private fun mediator(
        pageDao: FeedPageDao,
        gifDao: GifDao,
        api: upstreamApi,
        favorites: suspend () -> List<String>,
    ) = FeedMediator(
        feed = FeedSource.Favorites,
        gifDao = gifDao,
        pageDao = pageDao,
        api = api,
        pageSize = 20,
        now = { 1_000L + 5 }, // inside TTL of any cached page stamped at 1_000
        favorites = favorites,
    )

    private fun state(): PagingState<Int, Gif> =
        PagingState(pages = emptyList(), anchorPosition = null, config = PagingConfig(pageSize = 20), leadingPlaceholderCount = 0)

    @Test
    fun `cached empty favorites page is never fresh`() {
        val pageDao = FakeFeedPageDao()
        pageDao.pages["fav:v1:p1"] =
            FeedPageEntity(pageKey = "fav:v1:p1", gifIds = emptyList(), nextPageKey = null, fetchedAt = 1_000)
        val api = FakeApi()
        val result = runBlocking { mediator(pageDao, FakeGifDao(), api) { listOf("sweety.yuko") }.load(LoadType.REFRESH, state()) }
        assertTrue(result is MediatorResult.Success)
        assertFalse((result as MediatorResult.Success).endOfPaginationReached)
        assertEquals(1, api.userGifsCalls)
    }

    @Test
    fun `nothing favorited fetches nothing and writes no page row`() {
        val pageDao = FakeFeedPageDao()
        val api = FakeApi()
        val result = runBlocking { mediator(pageDao, FakeGifDao(), api) { emptyList() }.load(LoadType.REFRESH, state()) }
        assertTrue(result is MediatorResult.Success)
        assertTrue((result as MediatorResult.Success).endOfPaginationReached)
        assertEquals(0, api.userGifsCalls)
        assertTrue(pageDao.pages.isEmpty())
    }

    @Test
    fun `real favorites fetch round-robins creator and caches the page`() {
        val pageDao = FakeFeedPageDao()
        val gifDao = FakeGifDao()
        val api = FakeApi()
        val result = runBlocking { mediator(pageDao, gifDao, api) { listOf("sweety.yuko") }.load(LoadType.REFRESH, state()) }
        assertTrue(result is MediatorResult.Success)
        assertFalse((result as MediatorResult.Success).endOfPaginationReached)
        assertEquals(1, api.userGifsCalls)
        assertEquals(listOf("g1"), pageDao.pages["fav:v1:p1"]!!.gifIds)
        assertEquals("fav:v1:p2", pageDao.pages["fav:v1:p1"]!!.nextPageKey)
        assertEquals(1, gifDao.upserted.size)
        assertEquals("sweety.yuko", gifDao.upserted[0][0].userName)
    }
}

internal fun gifDtoShell(
    id: String,
    user: String,
): GifDtoShell =
    GifDtoShell(
        id = id,
        userName = user,
        tags = emptyList(),
        likes = 1,
        views = 1,
        duration = 1.0,
        hasAudio = false,
        width = 100,
        height = 100,
        createDate = 0,
        published = true,
        avgColor = "0,0,0",
        urls = UrlsDto(sd = "sd", hd = "hd", poster = "poster"),
    )
