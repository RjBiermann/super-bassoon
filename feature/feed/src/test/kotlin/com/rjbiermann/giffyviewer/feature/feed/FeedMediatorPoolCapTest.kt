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
import com.rjbiermann.giffyviewer.core.network.dto.GifsPageDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pool-cap 400 semantics on the mediator's cached-path APPEND (issue #46
 * follow-up): the trending pool hard-400s past its far end (live-proven,
 * ~5-6 pages × 20). The PAGER's walk already treats that 400 as end-of-pool
 * (FeedWalkTest contract); the MEDIATOR's APPEND must too —
 * `Success(endOfPaginationReached = true)`, NOT `MediatorResult.Error`. An
 * Error surfaces the row's "Couldn't load" copy for a genuinely-empty wall
 * and the warning + stack trace fire at every launch. Page-1 400s (BadOrder
 * discovery) stay errors.
 */
@OptIn(ExperimentalPagingApi::class)
class FeedMediatorPoolCapTest {
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

        override suspend fun gifsByIds(ids: List<String>): List<GifEntity> = throw NotImplementedError()

        override suspend fun evictStale(olderThan: Long) = throw NotImplementedError()

        override suspend fun evictBase(base: String) = throw NotImplementedError()

        override suspend fun evictFavorites(prefix: String) = throw NotImplementedError()
    }

    private fun pageWithGif(vararg ids: String): GifsPageDto = GifsPageDto(gifs = ids.map { gifDtoShell(it, "some.creator") })

    private fun state(): PagingState<Int, Gif> =
        PagingState(pages = emptyList(), anchorPosition = null, config = PagingConfig(pageSize = 20), leadingPlaceholderCount = 0)

    @Test
    fun `append hitting the pool-cap 400 ends pagination instead of erroring`() {
        val api = FakeApi()
        repeat(5) { p -> api.trendingPages[p + 1] = pageWithGif("t${p + 1}") }
        api.trendingHttpErrors[6] = 400
        val mediator =
            FeedMediator(
                feed = FeedSource.Trending,
                gifDao = FakeGifDao(),
                pageDao = FakeFeedPageDao(),
                api = api,
                pageSize = 20,
                now = { 0L },
            )
        runBlocking {
            mediator.load(LoadType.REFRESH, state())
            var result = mediator.load(LoadType.APPEND, state())
            var appends = 1
            while (
                result is MediatorResult.Success &&
                (result as MediatorResult.Success).endOfPaginationReached.not() &&
                appends < 10
            ) {
                result = mediator.load(LoadType.APPEND, state())
                appends++
            }
            assertTrue(result is MediatorResult.Success)
            assertTrue((result as MediatorResult.Success).endOfPaginationReached)
            assertEquals(6, api.trendingCalls.last())
        }
    }
}
