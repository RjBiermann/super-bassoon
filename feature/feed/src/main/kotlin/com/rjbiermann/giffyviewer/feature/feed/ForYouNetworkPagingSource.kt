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
    override suspend fun load(params: LoadParams<Int>): PagingSource.LoadResult<Int, Gif> {
        val page = params.key ?: 1
        return try {
            val ctx = forYouContext()
            val dto = api.feedForYou(count = pageSize, page = page)
            val gifs =
                dto.gifs
                    .map { it.toModel() }
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
            PagingSource.LoadResult.Page(
                data = gifs,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (dto.gifs.isEmpty()) null else page + 1,
            )
        } catch (t: Throwable) {
            PagingSource.LoadResult.Error(t)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Gif>): Int? =
        state.anchorPosition?.let { anchor -> state.closestPageToPosition(anchor)?.prevKey?.plus(1) }
}
