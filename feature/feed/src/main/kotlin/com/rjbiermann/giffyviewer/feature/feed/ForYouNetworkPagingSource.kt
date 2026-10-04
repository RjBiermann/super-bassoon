package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rjbiermann.giffyviewer.core.database.ContentFilter
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.model.matchesOrientation
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.dto.toModel

/**
 * For You feed (PLAN §7): reads the NETWORK live — never cached (same rule as
 * LikedNetworkPagingSource; the server personalizes per user, a Room cache
 * would serve another user's glob). ContentFilter still applies (leak-zero:
 * a blocked creator stays blocked even in For You).
 *
 * Scope (§7 Creators·Niches·All) is a READ-TIME filter over the server pages —
 * same "fetch once, three filters" shape as the cached FeedPagingSource path
 * (the server endpoint has no scope param); [forYouContext] is the repo's
 * 5-min-memoized loader (followed creators + joined niche tags).
 */
class ForYouNetworkPagingSource(
    private val api: GifsApi,
    private val contentFilter: ContentFilter,
    private val pageSize: Int,
    private val forYouContext: suspend () -> FeedRepository.ForYouContext,
    private val orientation: String = "any",
    private val verifiedOnly: Boolean = false,
) : PagingSource<Int, Gif>() {
    /** SLICE-15 (user: For You repeats videos then crashes): the server
     *  re-lists items across pages (upstream pagination overlap), so pages
     *  later than page 1 used to emit ids ALREADY live in this pager's list →
     *  duplicate grid keys → Paging measure-pass crash. The cached path's
     *  FeedPagingSource guards the same way; this is the network-live source's
     *  own per-PAGER set — a Refresh (pager
     *  restart) builds a fresh instance, so the fresh feed starts clean,
     *  same semantics as the cached path. */
    internal val seenIds = HashSet<String>()

    override suspend fun load(params: LoadParams<Int>): PagingSource.LoadResult<Int, Gif> {
        val firstPage = params.key ?: 1
        return try {
            // B1: same dead-end walk as LikedNetworkPagingSource (bounded
            // ≤ MAX_WALK_HOPS, sequential); B2: pool-cap 400 = graceful end.
            val ctx = forYouContext()
            var page = firstPage
            var gifs: List<Gif> = emptyList()
            var nextKey: Int? = null
            var hops = 0
            do {
                val dto =
                    try {
                        api.feedForYou(count = pageSize, page = page)
                    } catch (e: retrofit2.HttpException) {
                        // B2: pool-cap hard-400 = end of pool inside the walk.
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
                        // For You scope (§7): read-time, mirrors FeedPagingSource.
                        .filter {
                            when (ctx.scope) {
                                "creators" -> it.userName.lowercase() in ctx.followed
                                "niches" -> it.tags.any { t -> t.lowercase() in ctx.joinedTags }
                                else -> true
                            }
                        }
                nextKey = if (dto.gifs.isEmpty()) null else page + 1
                if (gifs.isEmpty()) {
                    // Dedup-aware B1 walk: a raw page can also empty by
                    // RE-LISTING (every id already shown by this pager, SLICE-15)
                    // — hop forward through it, else the feed starves on a
                    // dangling nextKey.
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
