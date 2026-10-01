package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.PagingSource
import com.rjbiermann.giffyviewer.core.database.ContentFilter
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
}
