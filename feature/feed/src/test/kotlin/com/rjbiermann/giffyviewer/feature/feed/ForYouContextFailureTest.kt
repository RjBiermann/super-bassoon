package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.PagingSource
import com.rjbiermann.giffyviewer.core.database.ContentFilter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #30: a failed For-You scope-context fetch (followed creators/niches)
 * must surface as an ERROR — never degrade to an empty scope filter that
 * rendered "Your For You feed is empty" for the wrong reason. The paging
 * source is where the UI-observable decision lands: a context throw inside
 * the load attempt → LoadResult.Error (the repository propagates its fetch
 * failure and memoizes only successes).
 */
class ForYouContextFailureTest {
    @Test
    fun `a failing context fetch lands as a load error, not an empty page`() =
        runTest {
            val source =
                ForYouNetworkPagingSource(
                    FakeApi(),
                    ContentFilter(),
                    20,
                    refreshBlocks = {},
                    forYouContext = { error("followed creators fetch failed (issue #30 contract)") },
                    orientation = "any",
                )
            val result = source.load(PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))
            assertTrue(result is PagingSource.LoadResult.Error)
        }
}
