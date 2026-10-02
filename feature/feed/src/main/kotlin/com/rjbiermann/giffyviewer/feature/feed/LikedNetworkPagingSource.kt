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
    override suspend fun load(params: LoadParams<Int>): PagingSource.LoadResult<Int, Gif> {
        val page = params.key ?: 1
        return try {
            val dto = api.likedFeed(count = pageSize, page = page)
            val gifs =
                dto.gifs
                    .map { it.toModel() }
                    .filter { contentFilter.allow(it.userName, it.tags, it.description) }
                    // Verified-only pref (2026-10): read-time, after the block filter.
                    .filter { !verifiedOnly || it.verified }
                    // Orientation pref (§6): read-time, not a block.
                    .filter { it.matchesOrientation(orientation) }
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
