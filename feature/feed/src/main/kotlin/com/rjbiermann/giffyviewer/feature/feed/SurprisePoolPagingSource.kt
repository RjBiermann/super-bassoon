package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rjbiermann.giffyviewer.core.model.Gif
import kotlinx.coroutines.flow.StateFlow

/** Static pager over the §8 "Surprise me" pool (regenerated per open). */
class SurprisePoolPagingSource(
    private val pool: StateFlow<List<Gif>?>,
) : PagingSource<Int, Gif>() {
    override suspend fun load(params: LoadParams<Int>): PagingSource.LoadResult<Int, Gif> {
        val page = params.key ?: 1
        val items = pool.value.orEmpty()
        val from = (page - 1) * PAGE
        val data = items.drop(from).take(PAGE)
        return PagingSource.LoadResult.Page(
            data = data,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (from + PAGE < items.size) page + 1 else null,
        )
    }

    override fun getRefreshKey(state: PagingState<Int, Gif>): Int? =
        state.anchorPosition?.let { anchor -> state.closestPageToPosition(anchor)?.prevKey?.plus(1) }

    companion object {
        const val PAGE = 20
    }
}
