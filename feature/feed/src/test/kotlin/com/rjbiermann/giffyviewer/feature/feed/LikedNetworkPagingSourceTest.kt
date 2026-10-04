package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.PagingSource
import com.rjbiermann.giffyviewer.core.database.ContentFilter
import com.rjbiermann.giffyviewer.core.network.dto.GifDtoShell
import com.rjbiermann.giffyviewer.core.network.dto.GifsPageDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Liked feed: reads the network live (never Room); pages end when empty. */
class LikedNetworkPagingSourceTest {
    @Test
    fun `liked feed reads network pages and ends at empty`() =
        runTest {
            val api = FakeApi()
            api.likedPages[1] =
                com.rjbiermann.giffyviewer.core.network.dto
                    .GifsPageDto(gifs = listOf(gifDtoShell("g1", "solarhelen")))
            val source = LikedNetworkPagingSource(api, ContentFilter(), 20)

            val p1 =
                source.load(PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))
            assertTrue(p1 is PagingSource.LoadResult.Page)
            assertEquals(1, (p1 as PagingSource.LoadResult.Page).data.size)
            assertEquals("solarhelen", p1.data[0].userName)
            assertEquals(2, p1.nextKey)

            val p2 =
                source.load(PagingSource.LoadParams.Append(key = 2, loadSize = 20, placeholdersEnabled = false))
            assertTrue(p2 is PagingSource.LoadResult.Page)
            assertEquals(0, (p2 as PagingSource.LoadResult.Page).data.size)
            assertEquals(null, (p2 as PagingSource.LoadResult.Page).nextKey)
        }

    @Test
    fun `dead-end walk hops past a fully-filtered raw page`() =
        runTest {
            // Page 1 = portrait-only (all dropped by the horizontal orientation
            // filter); page 2 carries a landscape gif — the walk must hop and
            // emit it instead of starving on a dangling nextKey (B1).
            val api = FakeApi()
            api.likedPages[1] = GifsPageDto(gifs = listOf(portraitGif("p1")))
            api.likedPages[2] = GifsPageDto(gifs = listOf(landscapeGif("p2")))
            val source = LikedNetworkPagingSource(api, ContentFilter(), 20, orientation = "horizontal")

            val result =
                source.load(PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))
            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            assertEquals(1, page.data.size)
            assertEquals("p2", page.data[0].id)
            assertEquals(3, page.nextKey)
            assertEquals(listOf(1, 2), api.likedFeedCalls)
        }

    @Test
    fun `pool-cap 400 inside the walk ends the pool gracefully`() =
        runTest {
            // The server hard-400s past the pool's far end (live-proven); the
            // walk must terminate as a graceful empty page (null nextKey), not
            // escape as LoadResult.Error → the mislabeled "You're offline".
            val api = FakeApi()
            api.likedPages[1] = GifsPageDto(gifs = listOf(portraitGif("p1")))
            api.likedHttpErrors[2] = 400
            val source = LikedNetworkPagingSource(api, ContentFilter(), 20, orientation = "horizontal")

            val result =
                source.load(PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))
            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            assertEquals(0, page.data.size)
            assertEquals(null, page.nextKey)
            assertEquals(listOf(1, 2), api.likedFeedCalls)
        }

    @Test
    fun `non-400 errors inside the walk keep normal propagation`() =
        runTest {
            val api = FakeApi()
            api.likedPages[1] = GifsPageDto(gifs = listOf(portraitGif("p1")))
            api.likedHttpErrors[2] = 500
            val source = LikedNetworkPagingSource(api, ContentFilter(), 20, orientation = "horizontal")

            val result =
                source.load(PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))
            assertTrue(result is PagingSource.LoadResult.Error)
            assertTrue((result as PagingSource.LoadResult.Error).throwable is retrofit2.HttpException)
        }

    @Test
    fun `walk bound stops after 8 consecutive filtered-empty hops`() =
        runTest {
            // 9 fully-filtered pages: hop on 1..8, stop at the bound with the
            // dangling nextKey (cached-path parity) instead of walking forever.
            val api = FakeApi()
            repeat(9) { p -> api.likedPages[p + 1] = GifsPageDto(gifs = listOf(portraitGif("p${p + 1}"))) }
            val source = LikedNetworkPagingSource(api, ContentFilter(), 20, orientation = "horizontal")

            val result =
                source.load(PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))
            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            assertEquals(0, page.data.size)
            assertEquals(10, page.nextKey)
            assertEquals(9, api.likedFeedCalls.size)
        }

    @Test
    fun `re-listed id from an earlier page is dropped (SLICE-15 session dedup)`() =
        runTest {
            // Upstream pagination overlap: page 2 re-lists page 1's id. Without
            // the dedup the Append emits a duplicate grid key → measure-pass
            // crash (user: For You repeats videos then crashes).
            val api = FakeApi()
            api.likedPages[1] = GifsPageDto(gifs = listOf(landscapeGif("g1")))
            api.likedPages[2] = GifsPageDto(gifs = listOf(landscapeGif("g1"), landscapeGif("g2")))
            val source = LikedNetworkPagingSource(api, ContentFilter(), 20)

            val p1 =
                source.load(PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))
            assertTrue(p1 is PagingSource.LoadResult.Page)
            assertEquals(listOf("g1"), (p1 as PagingSource.LoadResult.Page).data.map { it.id })

            val p2 =
                source.load(PagingSource.LoadParams.Append(key = 2, loadSize = 20, placeholdersEnabled = false))
            assertTrue(p2 is PagingSource.LoadResult.Page)
            val page = p2 as PagingSource.LoadResult.Page
            assertEquals(listOf("g2"), page.data.map { it.id })
            assertEquals(3, page.nextKey)
        }

    @Test
    fun `page deduping to zero hops forward and stops at the walk bound (SLICE-15)`() =
        runTest {
            // Pool exhausted by re-listing: pages 2..10 all re-list g1 — the
            // dedup-aware walk hops the empties (bounded, same MAX_WALK_HOPS)
            // and ends with the dangling nextKey (cached-path parity), never
            // emitting a duplicate key.
            val api = FakeApi()
            api.likedPages[1] = GifsPageDto(gifs = listOf(landscapeGif("g1")))
            repeat(9) { p -> api.likedPages[p + 2] = GifsPageDto(gifs = listOf(landscapeGif("g1"))) }
            val source = LikedNetworkPagingSource(api, ContentFilter(), 20)

            source.load(PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))
            val result =
                source.load(PagingSource.LoadParams.Append(key = 2, loadSize = 20, placeholdersEnabled = false))
            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            assertEquals(0, page.data.size)
            assertEquals(11, page.nextKey)
            // page 1 (Refresh) + pages 2..10 (the bounded walk) = 10 calls.
            assertEquals(10, api.likedFeedCalls.size)
        }
}

/** Portrait-shelled gif (height > width): dropped by a horizontal filter. */
internal fun portraitGif(id: String): GifDtoShell = gifDtoShell(id = id, user = "creator").copy(width = 50, height = 100)

/** Landscape-shelled gif (width > height): passes a horizontal filter. */
internal fun landscapeGif(id: String): GifDtoShell = gifDtoShell(id = id, user = "creator").copy(width = 200, height = 100)
