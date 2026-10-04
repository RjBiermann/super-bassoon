package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.database.FeedPageDao
import com.rjbiermann.giffyviewer.core.database.FeedPageEntity
import com.rjbiermann.giffyviewer.core.database.GifDao
import com.rjbiermann.giffyviewer.core.database.GifEntity
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.network.dto.GifsPageDto
import com.rjbiermann.giffyviewer.core.network.dto.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Slice A regression (device-verified wedge, Medium_Phone fresh profile,
 * global orientation=horizontal set before the first feed load): the cached
 * walk hopped past the trending pool's far end and the HTTP 400 escaped
 * `fetcher.fill` → source `LoadResult.Error` → with a RemoteMediator present
 * the presenter's combined refresh state stays LOADING when the source errors
 * while the mediator is idle (paging's computeHelperState) — the grid wedged:
 * no tiles, no empty-state message, an eternal spinner.
 *
 * Contract pinned: a 400 from beyond the pool terminates the walk as END OF
 * POOL (graceful empty page, null nextKey); non-400 errors keep propagating
 * (the source wraps them in LoadResult.Error as before).
 */
class FeedWalkTest {
    private class FakeGifDao : GifDao {
        override suspend fun upsertAll(gifs: List<GifEntity>) = Unit

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

        override suspend fun evictFavorites(prefix: String) = throw NotImplementedError()
    }

    /** Page walk driving a real FeedPageFetcher against the shared FakeApi —
     *  the exact fill path the mediator and the paging source share; the
     *  fetcher MUST share the walk's page dao (fill writes the rows it reads). */
    private fun fetcher(
        api: FakeApi,
        pageDao: FakeFeedPageDao,
    ) = FeedPageFetcher(FeedSource.Trending, FakeGifDao(), pageDao, api, 20)

    /** pageFor = the cached walk's acquisition step (the fix under test). */
    private suspend fun pageFor(
        api: FakeApi,
        pageDao: FakeFeedPageDao,
        page: Int,
    ): FeedPageEntity? = walkFill(FeedSource.Trending, pageDao, fetcher(api, pageDao), page)

    /** The strict-filter stand-in: every cached page reads as empty (the
     *  orientation=horizontal over an all-portrait pool repro). */
    private val allFilteredOut: suspend (page: Int, entity: FeedPageEntity?) -> List<Gif> =
        { _, _ -> emptyList() }

    @Test
    fun `400 past the pool cap inside the walk ends the walk as graceful end of pool`() =
        runBlocking {
            val api = FakeApi()
            repeat(5) { p -> api.trendingPages[p + 1] = pageWithGif("t${p + 1}") }
            api.trendingHttpErrors[6] = 400
            val pageDao = FakeFeedPageDao()

            val walked =
                walkFeedPages(
                    startPage = 1,
                    maxHops = MAX_WALK_HOPS,
                    pageFor = { pageFor(api, pageDao, it) },
                    nextKeyOf = { entity -> entity?.nextPageKey?.let { pageNumber(it) } },
                    gifsOf = allFilteredOut,
                )

            // End of pool, NOT an error: empty page with a null next key — the UI
            // shows the honest filtered-empty state instead of an eternal spinner.
            assertTrue(walked.gifs.isEmpty())
            assertNull(walked.nextKey)
            // The walk actually walked to the cap (bounded): 6 fetch attempts.
            assertEquals(listOf(1, 2, 3, 4, 5, 6), api.trendingCalls)
        }

    @Test
    fun `non-400 fill errors still propagate (source wraps them in LoadResult Error)`() =
        runBlocking {
            val api = FakeApi()
            api.trendingPages[1] = pageWithGif("t1")
            api.trendingHttpErrors[2] = 503
            val pageDao = FakeFeedPageDao()

            try {
                walkFeedPages(
                    startPage = 1,
                    maxHops = MAX_WALK_HOPS,
                    pageFor = { pageFor(api, pageDao, it) },
                    nextKeyOf = { entity -> entity?.nextPageKey?.let { pageNumber(it) } },
                    gifsOf = allFilteredOut,
                )
                fail("expected the 503 to propagate")
            } catch (e: retrofit2.HttpException) {
                assertEquals(503, e.code())
            }
        }

    @Test
    fun `first non-empty page stops the walk and carries its next key`() =
        runBlocking {
            val api = FakeApi()
            api.trendingPages[1] = pageWithGif("a", "b")
            api.trendingPages[2] = pageWithGif("c")
            val pageDao = FakeFeedPageDao()

            val walked =
                walkFeedPages(
                    startPage = 1,
                    maxHops = MAX_WALK_HOPS,
                    pageFor = { pageFor(api, pageDao, it) },
                    nextKeyOf = { entity -> entity?.nextPageKey?.let { pageNumber(it) } },
                    gifsOf = { _, entity -> entity?.gifIds?.map { gifDtoShell(it, "some.creator").toModel() } ?: emptyList() },
                )

            assertEquals(listOf("a", "b"), walked.gifs.map { it.id })
            assertEquals(2, walked.nextKey)
            assertEquals(1, api.trendingCalls.size)
        }

    private fun pageWithGif(vararg ids: String): GifsPageDto = GifsPageDto(gifs = ids.map { gifDtoShell(it, "some.creator") })
}
