package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.database.toModel
import com.rjbiermann.giffyviewer.core.database.weekStartMs
import com.rjbiermann.giffyviewer.core.model.Gif

/**
 * Reads ONLY Room (offline-first). Page rows carry ordered gif ids; `IN` queries
 * lose ordering, so results are re-ordered here.
 *
 * Custom PagingSources get NO automatic Room invalidation — we subscribe to the
 * invalidation tracker ourselves, otherwise the first feed load renders empty
 * even after the mediator has stored the data.
 */
class FeedPagingSource(
    private val db: GiffyDatabase,
    private val feed: FeedSource,
    private val pageDao: com.rjbiermann.giffyviewer.core.database.FeedPageDao,
    private val pageSize: Int,
    private val contentFilter: com.rjbiermann.giffyviewer.core.database.ContentFilter,
) : PagingSource<Int, Gif>() {
    init {
        // Only PREF tables invalidate: their changes are read-time filters that must
        // re-apply instantly (leak-zero). gifs/feed_pages writes from the mediator
        // must NOT invalidate — Paging already drives the next PagingSource load
        // after each APPEND, and invalidating mid-scroll restarts the generation,
        // empties the list for a beat and loses the scroll anchor.
        db.invalidationTracker.addObserver(
            object : androidx.room.InvalidationTracker.Observer("creator_prefs", "tag_prefs", "keyword_blocks") {
                override fun onInvalidated(tables: Set<String>) {
                    invalidate()
                }
            },
        )
    }

    override suspend fun load(params: LoadParams<Int>): PagingSource.LoadResult<Int, Gif> {
        val page = params.key ?: 1
        return try {
            val entity = pageDao.page(feedPageKey(feed, page))
            // upstream pagination overlaps: page n re-lists some of page n-1.
            // Paging requires unique keys → drop ids already shown by earlier pages.
            val seen =
                pageDao
                    .pagesForBase(feed.keyBase)
                    .filter { (pageNumber(it.pageKey) ?: 0) < page }
                    .flatMapTo(HashSet()) { it.gifIds }
            val ids = (entity?.gifIds ?: emptyList()).filterNot { it in seen }
            val byId = if (ids.isEmpty()) emptyMap() else pageDao.gifsByIds(ids).associateBy { it.id }
            // ContentFilter choke point (leak-zero): blocked creators/tags/keywords
            // never reach the UI, however they got into the cache.
            contentFilter.refreshFrom(db.contentPrefsDao())
            // Favorites feed keeps unfavorited rows out at read time (instant
            // un-favorite; the round-robin cache itself refreshes on TTL).
            val favs =
                if (feed is FeedSource.Favorites) {
                    db.contentPrefsDao().favoriteCreators().mapTo(HashSet()) { it.lowercase() }
                } else {
                    null
                }
            val models = ids.mapNotNull { byId[it]?.toModel() }
            // hide-count increment lives in the pipeline (PLAN §6): one batched
            // write per page load — recount on reload is accepted (read-time filter)
            val hidden = models.count { !contentFilter.allow(it.userName, it.tags) }
            if (hidden > 0) {
                db.contentPrefsDao().addHideCount(weekStartMs(System.currentTimeMillis()), hidden)
            }
            val gifs =
                models
                    .filter { contentFilter.allow(it.userName, it.tags) }
                    .filter { favs == null || it.userName.lowercase() in favs }
            // nextKey exists ONLY when the row was fetched (mediator fills it);
            // unfetched page → nextKey=null → APPEND waits for the mediator.
            val nextKey = entity?.nextPageKey?.let { pageNumber(it) }
            PagingSource.LoadResult.Page(
                data = gifs,
                prevKey = if (page == 1) null else page - 1,
                nextKey = nextKey,
            )
        } catch (t: Throwable) {
            PagingSource.LoadResult.Error(t)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Gif>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}
