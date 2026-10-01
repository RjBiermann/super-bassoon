package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.rjbiermann.giffyviewer.core.database.FeedPageDao
import com.rjbiermann.giffyviewer.core.database.GifDao
import com.rjbiermann.giffyviewer.core.network.upstreamApi
import java.io.IOException

/**
 * Cache-first page router (PLAN §5): Room is the source of truth. REFRESH
 * respects the feed's TTL; APPEND follows nextPageKey (or advances past the
 * highest cached page when the fast-scrolled source list ends unfetched).
 * The actual fetch+write lives in [FeedPageFetcher] — shared with
 * FeedPagingSource, which self-fills unfetched pages (Paging consults the
 * mediator only when the source's data is exhausted; a fast scroll otherwise
 * reaches an unfetched page and stalls the feed — user report).
 */
@OptIn(ExperimentalPagingApi::class)
class FeedMediator(
    private val feed: FeedSource,
    private val gifDao: GifDao,
    private val pageDao: FeedPageDao,
    api: upstreamApi,
    private val pageSize: Int,
    /** One-shot TTL bypass for this generation's REFRESH (PLAN §9 pull-to-refresh). */
    private val force: Boolean = false,
    private val now: () -> Long = System::currentTimeMillis,
    private val favorites: suspend () -> List<String> = { emptyList() },
) : RemoteMediator<Int, com.rjbiermann.giffyviewer.core.model.Gif>() {
    private val fetcher =
        FeedPageFetcher(feed, gifDao, pageDao, api, pageSize, now, favorites)

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, com.rjbiermann.giffyviewer.core.model.Gif>,
    ): MediatorResult =
        when (loadType) {
            LoadType.PREPEND -> MediatorResult.Success(endOfPaginationReached = true)

            LoadType.APPEND -> {
                // Fast scroll leaves the source's in-memory page list ending on an
                // EMPTY unfetched page (lastNext=null); Paging then consults us to
                // continue. Derive the page from the CACHE, not page 1 — deriving
                // from page 1 re-fetched page 2 forever (user report: feed "just
                // stops").
                val nextKey = state.pages.lastOrNull()?.nextKey
                val page =
                    nextKey
                        ?: pageDao
                            .pagesForBase(feed.keyBase)
                            .maxOfOrNull { pageNumber(it.pageKey) ?: 0 }
                            ?.plus(1)
                        ?: return MediatorResult.Success(endOfPaginationReached = true)
                runPage(page)
            }

            LoadType.REFRESH -> {
                // TTL: skip the network when the first page is still fresh.
                // Exception: a cached EMPTY Favorites page is only ever the
                // "nothing favorited yet" marker — treat it as always stale so the
                // first favorite appears immediately (regression: TV Favorites row
                // stayed empty).
                val cached = pageDao.page(feedPageKey(feed, 1))
                val staleEmpty = cached != null && feed is FeedSource.Favorites && cached.gifIds.isEmpty()
                if (!force && cached != null && now() - cached.fetchedAt < feed.ttlMs && !staleEmpty) {
                    MediatorResult.Success(endOfPaginationReached = false)
                } else {
                    // Nothing favorited → no fetch, and DON'T write an empty page
                    // row (it would pin "no favorites" in the cache until the
                    // TTL expires).
                    if (feed is FeedSource.Favorites && favorites().isEmpty()) {
                        MediatorResult.Success(endOfPaginationReached = true)
                    } else {
                        runPage(1)
                    }
                }
            }
        }

    private suspend fun runPage(page: Int): MediatorResult =
        try {
            val more = fetcher.fill(page)
            MediatorResult.Success(endOfPaginationReached = !more)
        } catch (e: IOException) {
            android.util.Log.w("FeedMediator", "${feed.keyBase} append p$page network error", e)
            MediatorResult.Error(e)
        } catch (e: Exception) {
            android.util.Log.w("FeedMediator", "${feed.keyBase} append p$page failed", e)
            MediatorResult.Error(e)
        }
}
