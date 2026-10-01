package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.database.toModel
import com.rjbiermann.giffyviewer.core.database.weekStartMs
import com.rjbiermann.giffyviewer.core.model.Gif
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

/** How long the refresh load waits for the mediator's first write (first-launch race). */
private const val CACHE_WAIT_MS = 20_000L

/**
 * Reads ONLY Room (offline-first). Page rows carry ordered gif ids; `IN` queries
 * lose ordering, so results are re-ordered here.
 *
 * Custom PagingSources get NO automatic Room invalidation — we subscribe to the
 * invalidation tracker for the PREF tables only: their changes are read-time
 * filters that must re-apply instantly (leak-zero).
 *
 * Deliberately NOT watching feed_pages: every mediator write (including each
 * APPEND) would invalidate every live pager; the restart re-anchors and APPENDs
 * again — an endless background fetch storm (seen live on TV: rows kept
 * "loading" new videos forever). The first-launch race this guards against is
 * instead handled by WAITING for the cache inside the refresh load: the
 * mediator fills the page row while our load is already running, so we listen
 * for that row (bounded) and return the data directly.
 */
class FeedPagingSource(
    private val db: GiffyDatabase,
    private val feed: FeedSource,
    private val pageDao: com.rjbiermann.giffyviewer.core.database.FeedPageDao,
    private val pageSize: Int,
    private val contentFilter: com.rjbiermann.giffyviewer.core.database.ContentFilter,
    private val forYouContext: suspend () -> com.rjbiermann.giffyviewer.feature.feed.FeedRepository.ForYouContext =
        {
            com.rjbiermann.giffyviewer.feature.feed.FeedRepository
                .ForYouContext("all", emptySet(), emptySet())
        },
) : PagingSource<Int, Gif>() {
    init {
        db.invalidationTracker.addObserver(
            object : androidx.room.InvalidationTracker.Observer(
                "creator_prefs",
                "tag_prefs",
                "keyword_blocks",
                "niche_groups",
            ) {
                override fun onInvalidated(tables: Set<String>) {
                    invalidate()
                }
            },
        )
    }

    override suspend fun load(params: LoadParams<Int>): PagingSource.LoadResult<Int, Gif> {
        val page = params.key ?: 1
        return try {
            var entity = pageDao.page(feedPageKey(feed, page))
            // First-launch race: the refresh load can hit an empty cache before
            // the mediator's network write lands — returning empty here used to
            // strand the grid until process restart (seen live on TV). Instead,
            // wait a bounded time for the page row to appear; Favorites writes
            // no row when nothing is favorited, so it never waits.
            if (entity == null && page == 1 && params is LoadParams.Refresh && feed !is FeedSource.Favorites) {
                entity =
                    withTimeoutOrNull(CACHE_WAIT_MS) {
                        pageDao
                            .pageFlow(feedPageKey(feed, 1))
                            .firstOrNull { it != null }
                    }
            }
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
            // never reach the UI, however they got into the cache. BLOCKED group
            // tags ride the same reload (§6 stage 2).
            contentFilter.refreshFrom(db.contentPrefsDao())
            contentFilter.refreshGroupTags(db.nicheGroupDao())
            // Favorites feed keeps unfavorited rows out at read time (instant
            // un-favorite; the round-robin cache itself refreshes on TTL).
            val scopeCtx = if (feed is FeedSource.ForYou) forYouContext() else null
            val favs =
                if (feed is FeedSource.Favorites) {
                    db.contentPrefsDao().favoriteCreators().mapTo(HashSet()) { it.lowercase() }
                } else {
                    null
                }
            val models = ids.mapNotNull { byId[it]?.toModel() }
            // hide-count increment lives in the pipeline (PLAN §6): one batched
            // write per page load — recount on reload is accepted (read-time filter)
            val hidden = models.count { !contentFilter.allow(it.userName, it.tags, it.description) }
            if (hidden > 0) {
                db.contentPrefsDao().addHideCount(weekStartMs(System.currentTimeMillis()), hidden)
            }
            val gifs =
                models
                    .filter { contentFilter.allow(it.userName, it.tags, it.description) }
                    .filter { favs == null || it.userName.lowercase() in favs }
                    // For You scope (§7 Creators·Niches·All): read-time filter over
                    // the SAME cached server pages — one fetch, three filters.
                    .filter {
                        when (scopeCtx?.scope) {
                            "creators" -> it.userName.lowercase() in scopeCtx!!.followed
                            "niches" -> it.tags.any { t -> t.lowercase() in scopeCtx!!.joinedTags }
                            else -> true
                        }
                    }
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
