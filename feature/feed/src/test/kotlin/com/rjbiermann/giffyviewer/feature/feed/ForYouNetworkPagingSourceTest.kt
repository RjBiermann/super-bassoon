package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.PagingSource
import com.rjbiermann.giffyviewer.core.database.ContentFilter
import com.rjbiermann.giffyviewer.core.network.dto.GifsPageDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** For You feed (network-live): the B1 dead-end walk + B2 pool-cap shape. */
class ForYouNetworkPagingSourceTest {
    private fun source(api: FakeApi) =
        ForYouNetworkPagingSource(
            api,
            ContentFilter(),
            20,
            refreshBlocks = {},
            forYouContext = { FeedRepository.ForYouContext("all", emptySet(), emptySet()) },
            orientation = "horizontal",
        )

    @Test
    fun `dead-end walk hops past a fully-filtered raw page`() =
        runTest {
            val api = FakeApi()
            api.forYouPages[1] = GifsPageDto(gifs = listOf(portraitGif("p1")))
            api.forYouPages[2] = GifsPageDto(gifs = listOf(landscapeGif("p2")))
            val result = source(api).load(PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))
            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            assertEquals(1, page.data.size)
            assertEquals("p2", page.data[0].id)
            assertEquals(3, page.nextKey)
            assertEquals(listOf(1, 2), api.forYouCalls)
        }

    @Test
    fun `pool-cap 400 inside the walk ends the pool gracefully`() =
        runTest {
            val api = FakeApi()
            api.forYouPages[1] = GifsPageDto(gifs = listOf(portraitGif("p1")))
            api.forYouHttpErrors[2] = 400
            val result = source(api).load(PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))
            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            assertEquals(0, page.data.size)
            assertEquals(null, page.nextKey)
        }

    @Test
    fun `non-400 errors keep normal propagation`() =
        runTest {
            val api = FakeApi()
            api.forYouPages[1] = GifsPageDto(gifs = listOf(portraitGif("p1")))
            api.forYouHttpErrors[2] = 503
            val result = source(api).load(PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))
            assertTrue(result is PagingSource.LoadResult.Error)
        }

    @Test
    fun `re-listed ids are dropped and a fully-deduped page hops (SLICE-15)`() =
        runTest {
            // User report: For You repeats videos then crashes — the server
            // re-lists items across pages; duplicate grid keys crash the
            // measure pass. Dedup drops the repeat; a fully-deduped page hops
            // forward (dedup-aware B1 walk, bounded).
            val api = FakeApi()
            api.forYouPages[1] = GifsPageDto(gifs = listOf(landscapeGif("g1")))
            api.forYouPages[2] = GifsPageDto(gifs = listOf(landscapeGif("g1"), landscapeGif("g2")))
            // Fully re-listed page → dedups to zero → walk hops to page 4.
            api.forYouPages[3] = GifsPageDto(gifs = listOf(landscapeGif("g1")))
            api.forYouPages[4] = GifsPageDto(gifs = listOf(landscapeGif("g4")))
            val src = source(api)

            val p1 = src.load(PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))
            assertTrue(p1 is PagingSource.LoadResult.Page)
            assertEquals(listOf("g1"), (p1 as PagingSource.LoadResult.Page).data.map { it.id })

            val p2 = src.load(PagingSource.LoadParams.Append(key = 2, loadSize = 20, placeholdersEnabled = false))
            assertTrue(p2 is PagingSource.LoadResult.Page)
            assertEquals(listOf("g2"), (p2 as PagingSource.LoadResult.Page).data.map { it.id })

            val p3 = src.load(PagingSource.LoadParams.Append(key = 3, loadSize = 20, placeholdersEnabled = false))
            assertTrue(p3 is PagingSource.LoadResult.Page)
            val page = p3 as PagingSource.LoadResult.Page
            // g1 re-listed → dropped, hop to page 4 for the fresh item.
            assertEquals(listOf("g4"), page.data.map { it.id })
            assertEquals(5, page.nextKey)
            assertEquals(listOf(3, 4), api.forYouCalls.subList(2, api.forYouCalls.size))
        }

    @Test
    fun `load refreshes the block sets and drops a newly blocked creator (#42)`() =
        runTest {
            // Issue #42 contract: the source runs the repository's refresh
            // closure BEFORE filtering, so a creator blocked while the home
            // page sat on this network-live feed is dropped from the very
            // first page (previously the singleton's stale sets let it
            // through). The closure simulates the repo's DAO-backed refresher
            // via ContentFilter's list-based overload.
            val api = FakeApi()
            api.forYouPages[1] =
                GifsPageDto(
                    gifs =
                        listOf(
                            gifDtoShell("p1", "creatorofp1"),
                            gifDtoShell("p2", "creatorofp2"),
                        ),
                )
            var refreshes = 0
            val filter = ContentFilter()
            val src =
                ForYouNetworkPagingSource(
                    api,
                    filter,
                    20,
                    refreshBlocks = {
                        refreshes++
                        filter.refreshFrom(listOf("creatorofp1"), emptyList(), emptyList())
                    },
                    forYouContext = { FeedRepository.ForYouContext("all", emptySet(), emptySet()) },
                )
            val result = src.load(PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))
            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            assertEquals(1, refreshes)
            assertEquals(listOf("p2"), page.data.map { it.id })
        }
}
