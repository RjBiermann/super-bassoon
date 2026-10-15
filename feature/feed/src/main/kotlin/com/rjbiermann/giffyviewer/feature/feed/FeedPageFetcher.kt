package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.database.FeedPageDao
import com.rjbiermann.giffyviewer.core.database.FeedPageEntity
import com.rjbiermann.giffyviewer.core.database.GifDao
import com.rjbiermann.giffyviewer.core.database.toEntity
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.dto.GifsPageDto
import com.rjbiermann.giffyviewer.core.network.dto.toModel

/**
 * One page = one fetch + cache write, shared by the RemoteMediator and the
 * paging source (the source fills an unfetched page itself: Paging consults
 * the mediator only when the source's data is exhausted, so a fast scroll
 * used to reach an unfetched page and stall the feed — user report).
 * Rate-limit invariant: one API request per call.
 */
class FeedPageFetcher(
    private val feed: FeedSource,
    private val gifDao: GifDao,
    private val pageDao: FeedPageDao,
    private val api: GifsApi,
    private val pageSize: Int,
    private val now: () -> Long = System::currentTimeMillis,
    private val favorites: suspend () -> List<String> = { emptyList() },
    /** #5 shuffle-on-refresh rotation seed (Favorites only; 0 = DB order). */
    private val favoritesSeed: () -> Long = { 0L },
) {
    /** Fetches and caches [page]; false = nothing fetched (empty/degenerate). */
    suspend fun fill(page: Int): Boolean {
        // Favorites rotation size (#5): one local DB read per fill — read
        // separately from fetch's pool read, so the END decision below uses the
        // same size the cached row's mapping assumed. A mid-fill favorites edit
        // shifts the mapping entirely (accepted — a new generation refetches).
        val rotationSize = if (feed is FeedSource.Favorites) favorites().size else 0
        val dto = fetch(page)
        val gifs = dto.gifs.map { it.toModel().toEntity(now()) }
        if (feed is FeedSource.Favorites && gifs.isEmpty() && rotationSize == 0) {
            // Nothing favorited → don't pin an empty "no favorites" row (§5).
            return false
        }
        val nowMs = now()
        gifDao.upsertAll(gifs)
        // #5 empty-slot semantics: an empty page EARLY in a rotation is not the
        // feed's end — a later rotation slot may still carry inner pages. The
        // row chains forward (nextPageKey = page + 1) so the source's filter
        // walk and the mediator's APPEND keep advancing; an empty LAST slot of
        // a rotation is the honest end (one API call per fetch: the next
        // rotation's first slot is not probed — bounded by the walk budget).
        val more =
            gifs.isNotEmpty() ||
                (feed is FeedSource.Favorites && rotationSize > 0 && page % rotationSize != 0)
        pageDao.upsert(
            FeedPageEntity(
                pageKey = feedPageKey(feed, page),
                gifIds = gifs.map { it.id },
                nextPageKey = if (more) feedPageKey(feed, page + 1) else null,
                fetchedAt = nowMs,
            ),
        )
        return more
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
                // The mediator guards an empty favorite set, but the paging source
                // self-fills unfetched pages too — a fresh install with zero favorited
                // creators walks an unfetched Favorites page → degenerate empty page,
                // not a divide-by-zero (live-proven on TV36).
                // #5 shuffle-on-refresh: the seed rotates the creator ORDER; the
                // inner-page math (page n → slots (n-1)/size + 1) is unchanged.
                val rotated = rotateCreators(favorites(), favoritesSeed())
                if (rotated.isEmpty()) {
                    GifsPageDto()
                } else {
                    api.userGifs(
                        username = rotated[(page - 1) % rotated.size],
                        count = pageSize,
                        page = (page - 1) / rotated.size + 1,
                    )
                }
            }
            is FeedSource.ForYou -> api.feedForYou(count = pageSize, page = page)
            is FeedSource.Niche ->
                api.nicheGifs(
                    nicheId = feed.id,
                    count = pageSize,
                    page = page,
                    // Live drift 2026-10: "trending" is now a BadOrder — the
                    // server's default ordering rides on an omitted param.
                    order = feed.sort.ifEmpty { null },
                )
            is FeedSource.Creator ->
                api.userGifs(
                    username = feed.username,
                    count = pageSize,
                    page = page,
                    order = feed.sort.ifEmpty { "trending" },
                )
            is FeedSource.Custom -> {
                // Round-robin across the blended refs ("creator:<username>" /
                // "tag:<text>" / "niche:<id>|<name>"; groups are expanded to
                // their tags by the builder). Refs never shrink mid-generation
                // (definitions are edited in the builder), so the mapping is
                // stable. Empty feeds are valid (filled later via quick-add)
                // and land an empty page here.
                val refs = feed.refs
                if (refs.isEmpty()) {
                    GifsPageDto()
                } else {
                    val ref = refs[(page - 1) % refs.size]
                    val inner = (page - 1) / refs.size + 1
                    when {
                        ref.startsWith("creator:") ->
                            api.userGifs(username = ref.removePrefix("creator:"), count = pageSize, page = inner)
                        ref.startsWith("niche:") ->
                            api.nicheGifs(
                                nicheId = parseNicheRef(ref)?.first ?: "",
                                count = pageSize,
                                page = inner,
                            )
                        else ->
                            api.search(
                                searchText = ref.removePrefix("tag:"),
                                count = pageSize,
                                page = inner,
                                order = "trending",
                            )
                    }
                }
            }
            is FeedSource.TopThisWeek -> api.trendingPopular(order = "top_week", count = pageSize, page = page)
            else -> api.trendingPopular(count = pageSize, page = page)
        }
}

/** #5 shuffle-on-refresh rotation: creators rotated forward by
 *  ((seed % size) + size) % size — modulo-safe for negative seeds. Rotation
 *  preserves the round-robin inner-page math (page n still maps to slots
 *  (n-1)/size + 1); only the creator ORDER of the mapping rotates. Seed 0 =
 *  plain DB order (the pre-rotation first-load behavior). The seed is volatile
 *  repository state (reshuffleFavorites) and never persisted. */
internal fun rotateCreators(
    creators: List<String>,
    seed: Long,
): List<String> {
    if (creators.isEmpty() || seed == 0L) return creators
    val at =
        (((seed % creators.size) + creators.size) % creators.size)
            .toInt()
    return creators.drop(at) + creators.take(at)
}
