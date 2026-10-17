package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rjbiermann.giffyviewer.core.database.ContentFilter
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.model.matchesOrientation
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.dto.toModel

/**
 * Liked feed (PLAN §7): reads the NETWORK live — never cached (§5 no-cache rule;
 * no Room reads, no feed_pages rows, no mediator). ContentFilter still applies
 * (leak-zero: a blocked creator stays blocked even in your own likes).
 */
class LikedNetworkPagingSource(
    private val api: GifsApi,
    private val contentFilter: ContentFilter,
    private val pageSize: Int,
    private val orientation: String = "any",
    private val verifiedOnly: Boolean = false,
) : PagingSource<Int, Gif>() {
    /** SLICE-15: per-pager session dedup — same upstream-pagination overlap
     *  crash as For You (duplicate grid keys from re-listed ids); a Refresh
     *  builds a fresh instance so the fresh feed starts clean. */
    internal val seenIds = HashSet<String>()

    override suspend fun load(params: LoadParams<Int>): PagingSource.LoadResult<Int, Gif> {
        val firstPage = params.key ?: 1
        return try {
            // B1 (dead-end walk, same shape as FeedPagingSource's cached-path
            // walk): a raw page whose items are ALL dropped by the read-time
            // filters used to emit data=[] with a dangling nextKey — 0 visible
            // items → no scroll → no APPEND → the feed starves forever. Hop
            // forward through those pages (bounded ≤ MAX_WALK_HOPS, sequential
            // — rate-limit invariant).
            var page = firstPage
            var gifs: List<Gif> = emptyList()
            var nextKey: Int? = null
            var hops = 0
            do {
                val dto =
                    try {
                        api.likedFeed(count = pageSize, page = page)
                    } catch (e: retrofit2.HttpException) {
                        // B2: past the pool's far end the server hard-400s —
                        // inside the walk that is END OF POOL (graceful empty
                        // page), not an error to surface as "You're offline".
                        if (e.code() == 400) {
                            nextKey = null
                            break
                        }
                        throw e
                    }
                gifs =
                    dto.gifs
                        .map { it.toModel() }
                        // SLICE-15 session dedup FIRST — a re-listed id must not
                        // survive the filters just because it passes them.
                        .filter { seenIds.add(it.id) }
                        .filter { contentFilter.allow(it.userName, it.tags, it.description) }
                        // Verified-only pref (2026-10): read-time, after the block filter.
                        .filter { !verifiedOnly || it.verified }
                        // Orientation pref (§6): read-time, not a block.
                        .filter { it.matchesOrientation(orientation) }
                nextKey = if (dto.gifs.isEmpty()) null else page + 1
                if (gifs.isEmpty()) {
                    // Dedup-aware B1 walk: a raw page can now also empty by
                    // RE-LISTING (every id already shown by this pager, SLICE-15)
                    // — hop forward through it, else the feed starves on a
                    // dangling nextKey (the crash class that forced no explicit
                    // zero-data emission in the first place).
                    if (nextKey != null && nextKey > page && hops < MAX_WALK_HOPS) {
                        hops++
                        page = nextKey
                    } else {
                        break
                    }
                } else {
                    break
                }
            } while (gifs.isEmpty())
            PagingSource.LoadResult.Page(
                data = gifs,
                prevKey = if (firstPage == 1) null else firstPage - 1,
                nextKey = nextKey,
            )
        } catch (t: Throwable) {
            PagingSource.LoadResult.Error(t)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Gif>): Int? =
        state.anchorPosition?.let { anchor -> state.closestPageToPosition(anchor)?.prevKey?.plus(1) }
}
