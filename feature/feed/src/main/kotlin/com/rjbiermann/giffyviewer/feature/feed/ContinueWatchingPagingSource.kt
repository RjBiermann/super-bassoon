package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.model.Gif

/** "Continue Watching" pager (§8, mobile): Room-only, no RemoteMediator — the
 *  list is re-derived on every load via [derive]. Custom PagingSources get NO
 *  automatic Room invalidation (AGENTS-DATABASE pitfall), and this surface
 *  reads pref tables too (read-time filters — leak-zero): like FeedPagingSource
 *  it subscribes to the invalidation tracker itself. watch_history + gifs ride
 *  the same observer so finishing a video drops the row and a new partial watch
 *  adds one without any pager restart from the repository. */
class ContinueWatchingPagingSource(
    private val db: GiffyDatabase,
    private val derive: suspend () -> List<Gif>,
) : PagingSource<Int, Gif>() {
    init {
        db.invalidationTracker.addObserver(
            object : androidx.room.InvalidationTracker.Observer(
                "watch_history",
                "gifs",
                "creator_prefs",
                "tag_prefs",
                "keyword_blocks",
                "custom_feeds",
            ) {
                override fun onInvalidated(tables: Set<String>) {
                    invalidate()
                }
            },
        )
    }

    override suspend fun load(params: LoadParams<Int>): PagingSource.LoadResult<Int, Gif> {
        val items = derive()
        val page = params.key ?: 1
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
