package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.rjbiermann.giffyviewer.core.database.FeedPageDao
import com.rjbiermann.giffyviewer.core.database.FeedPageEntity
import com.rjbiermann.giffyviewer.core.database.GifDao
import com.rjbiermann.giffyviewer.core.database.toEntity
import com.rjbiermann.giffyviewer.core.network.upstreamApi
import com.rjbiermann.giffyviewer.core.network.dto.GifsPageDto
import com.rjbiermann.giffyviewer.core.network.dto.toModel
import java.io.IOException

/**
 * Cache-first page fetcher (PLAN §5): Room is the source of truth; this only
 * fills gaps. REFRESH respects the feed's TTL; APPEND follows nextPageKey.
 * On failure (airplane mode etc.) the error surfaces to Paging, cached rows
 * still render — the offline invariant.
 */
@OptIn(ExperimentalPagingApi::class)
class FeedMediator(
    private val feed: FeedSource,
    private val gifDao: GifDao,
    private val pageDao: FeedPageDao,
    private val api: upstreamApi,
    private val pageSize: Int,
    /** One-shot TTL bypass for this generation's REFRESH (PLAN §9 pull-to-refresh). */
    private val force: Boolean = false,
    private val now: () -> Long = System::currentTimeMillis,
    private val favorites: suspend () -> List<String> = { emptyList() },
) : RemoteMediator<Int, com.rjbiermann.giffyviewer.core.model.Gif>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, com.rjbiermann.giffyviewer.core.model.Gif>,
    ): MediatorResult {
        val page: Int =
            when (loadType) {
                LoadType.REFRESH -> 1

                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)

                LoadType.APPEND -> {
                    val nextKey = state.pages.lastOrNull()?.nextKey
                    val nextPage =
                        nextKey ?: pageNumber(pageDao.page(feedPageKey(feed, 1))?.nextPageKey)
                            ?: return MediatorResult.Success(endOfPaginationReached = true)
                    nextPage
                }
            }

        // TTL check: skip the network when REFRESH and the first page is still fresh.
        // Exception: a cached EMPTY Favorites page is only ever the "nothing favorited
        // yet" marker — treat it as always stale so the first favorite appears
        // immediately instead of after the TTL runs out.
        if (loadType == LoadType.REFRESH && !force) {
            val cached = pageDao.page(feedPageKey(feed, 1))
            val staleEmpty = cached != null && feed is FeedSource.Favorites && cached.gifIds.isEmpty()
            if (cached != null && now() - cached.fetchedAt < feed.ttlMs && !staleEmpty) {
                return MediatorResult.Success(endOfPaginationReached = false)
            }
        }
        // Nothing favorited → no fetch, and DON'T write an empty page row (it would
        // pin "no favorites" in the cache until the TTL expires).
        if (feed is FeedSource.Favorites && favorites().isEmpty()) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        return try {
            val dto = fetch(page)
            val nowMs = now()
            val gifs = dto.gifs.map { it.toModel().toEntity(nowMs) }
            gifDao.upsertAll(gifs)
            val nextPage = if (gifs.isEmpty()) null else feedPageKey(feed, page + 1)
            pageDao.upsert(
                FeedPageEntity(
                    pageKey = feedPageKey(feed, page),
                    gifIds = gifs.map { it.id },
                    nextPageKey = nextPage,
                    fetchedAt = nowMs,
                ),
            )
            MediatorResult.Success(endOfPaginationReached = gifs.isEmpty() || nextPage == null)
        } catch (e: IOException) {
            android.util.Log.w("FeedMediator", "${feed.keyBase} load($loadType, p$page) network error", e)
            MediatorResult.Error(e)
        } catch (e: Exception) {
            android.util.Log.w("FeedMediator", "${feed.keyBase} load($loadType, p$page) failed", e)
            MediatorResult.Error(e)
        }
    }

    private suspend fun fetch(page: Int): GifsPageDto =
        when (feed) {
            is FeedSource.Search ->
                api.search(
                    searchText = feed.query,
                    count = pageSize,
                    page = page,
                    order = feed.sort.ifEmpty { "trending" },
                )
            is FeedSource.Favorites -> {
                // ponytail: page n maps to creator[(n-1) % n_creators] at fetch time;
                // a changed favorite set shifts the mapping until the cache refreshes
                // (read-time filter in FeedPagingSource keeps unfavorited rows out).
                // Non-empty is guaranteed by the early return in load().
                val creators = favorites()
                api.userGifs(
                    username = creators[(page - 1) % creators.size],
                    count = pageSize,
                    page = (page - 1) / creators.size + 1,
                )
            }
            is FeedSource.ForYou -> api.feedForYou(count = pageSize, page = page)
            is FeedSource.Niche ->
                api.nicheGifs(
                    nicheId = feed.id,
                    count = pageSize,
                    page = page,
                    order = feed.sort.ifEmpty { "trending" },
                )
            is FeedSource.Group -> {
                // ponytail: page n maps to tag[(n-1) % n_tags]; a changed tag list
                // shifts the mapping until the cache refreshes (same pattern as
                // the Favorites creator rotation; dedup handled by the source).
                // Groups UI requires 1+ tags, so empty is a degenerate safe path.
                val tags = feed.tags
                if (tags.isEmpty()) {
                    GifsPageDto()
                } else {
                    api.search(
                        searchText = tags[(page - 1) % tags.size],
                        count = pageSize,
                        page = (page - 1) / tags.size + 1,
                        order = "trending",
                    )
                }
            }
            is FeedSource.Creator ->
                api.userGifs(
                    username = feed.username,
                    count = pageSize,
                    page = page,
                    order = feed.sort.ifEmpty { "trending" },
                )
            is FeedSource.TopThisWeek -> api.trendingPopular(order = "top_week", count = pageSize, page = page)
            else -> api.trendingPopular(count = pageSize, page = page)
        }
}
