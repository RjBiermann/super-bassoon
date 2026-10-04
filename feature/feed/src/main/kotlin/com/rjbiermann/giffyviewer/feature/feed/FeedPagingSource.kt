package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rjbiermann.giffyviewer.core.database.ContentPrefsDao
import com.rjbiermann.giffyviewer.core.database.CustomFeedDao
import com.rjbiermann.giffyviewer.core.database.FeedPageDao
import com.rjbiermann.giffyviewer.core.database.FeedPageEntity
import com.rjbiermann.giffyviewer.core.database.toModel
import com.rjbiermann.giffyviewer.core.database.weekStartMs
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.model.matchesOrientation
import com.rjbiermann.giffyviewer.core.model.resolutionMatches

/** §8 untagged-only (tag-bundle feeds — the merged groups): tags must stay
 *  inside the bundle. Applies to custom feeds whose refs are ALL tag refs —
 *  a feed that blends creators/niches has no single bundle to bound to. */
internal fun untagged(
    gif: Gif,
    feed: FeedSource,
    prefs: com.rjbiermann.giffyviewer.core.datastore.FeedPrefs,
): Boolean {
    if (!prefs.untaggedOnly || feed !is FeedSource.Custom) return true
    val refs = feed.refs
    if (refs.isEmpty() || refs.any { it.startsWith("creator:") || it.startsWith("niche:") }) return true
    // Non-empty required: an untagged gif carries no bundle signal at all
    // (vacuous "all{}" would let every untagged gif through — live-proven).
    val bundle = refs.map { it.removePrefix("tag:").lowercase() }.toSet()
    return gif.tags.isNotEmpty() && gif.tags.all { it.lowercase() in bundle }
}

/** §8 shuffle: deterministic per-seed order — hashing every id with the seed
 *  yields ONE global order across all pages (stable across recomposition;
 *  Reshuffle = new seed). Seed mixed via splitmix-style XOR: affine keys
 *  (h+c, h*c) sort identically for every seed — XOR of a seed-scaled constant
 *  actually permutes the order. Shared with the repository's Continue branch. */
internal fun shuffleOrdered(
    gifs: List<Gif>,
    seed: Long,
): List<Gif> =
    if (seed == 0L) {
        gifs
    } else {
        gifs.sortedBy { it.id.hashCode().toLong() xor (seed * -7046029254386353131L) }
    }

/** How long the refresh load waits for the mediator's first write (first-launch race). */
private const val CACHE_WAIT_MS = 20_000L

/** Filter walk budget: consecutive fully-filtered-empty pages hopped per load
 *  (each hop may fetch one page — the 15 req/5s browser rate budget bounds it). */
internal const val MAX_WALK_HOPS = 8

/** One walk outcome: the page's filtered gifs + the pager's next key. */
internal data class WalkedPage(
    val gifs: List<Gif>,
    val nextKey: Int?,
)

/** One walk iteration's page acquisition: read the cached row; an unfetched
 *  page is self-filled here through the shared fetcher (fast-scroll race — same
 *  fetcher the mediator uses → one API call, rate-limit safe).
 *
 *  B2 on the cached path (mirrors the network-live sources' catch): past the
 *  server pool's far end the fetch hard-400s (live-proven: trending pool ~100
 *  items → HTTP 400 beyond the last page). Inside the walk that is END OF POOL
 *  — a null entity lets the walk's no-next-page tail terminate gracefully
 *  (empty Page, null nextKey). Letting it escape used to surface as a source
 *  LoadResult.Error that the presenter never shows: with a RemoteMediator the
 *  combined refresh state stays LOADING when the source errors while the
 *  mediator is idle (computeHelperState) — the device-verified wedge: no tiles,
 *  no empty-state message, an eternal spinner. Any non-400 error keeps the
 *  normal Error propagation. */
internal suspend fun walkFill(
    feed: FeedSource,
    pageDao: FeedPageDao,
    fetcher: FeedPageFetcher?,
    page: Int,
): FeedPageEntity? {
    val cached = pageDao.page(feedPageKey(feed, page))
    if (cached != null) return cached
    try {
        fetcher?.fill(page)
    } catch (e: retrofit2.HttpException) {
        if (e.code() == 400) return null
        throw e
    }
    return pageDao.page(feedPageKey(feed, page))
}

/** The §8 filter dead-end walk (live-proven): a strict client filter can empty
 *  EVERY cached page; with 0 visible items nothing scrolls → no APPEND → the
 *  feed starves forever. Hop forward through consecutive pages (bounded:
 *  [maxHops] — each hop may fetch one page, the 15 req/5s browser rate budget
 *  bounds it). Per iteration [pageFor] resolves the page row (a 400 from
 *  beyond the pool returns null = end of pool, see [walkFill]), [gifsOf] runs
 *  the page's full read-time filter chain, [nextKeyOf] parses the pager key.
 *  Stops at the first non-empty page (returns it + its next key) or when the
 *  chain of empties runs out (graceful empty page). */
internal suspend fun walkFeedPages(
    startPage: Int,
    maxHops: Int,
    pageFor: suspend (page: Int) -> FeedPageEntity?,
    nextKeyOf: (entity: FeedPageEntity?) -> Int?,
    gifsOf: suspend (page: Int, entity: FeedPageEntity?) -> List<Gif>,
): WalkedPage {
    var page = startPage
    var gifs: List<Gif> = emptyList()
    var nextKey: Int? = null
    var empties = 0
    do {
        val entity = pageFor(page)
        val pageGifs = gifsOf(page, entity)
        if (pageGifs.isNotEmpty()) {
            gifs = pageGifs
            nextKey = nextKeyOf(entity)
        } else {
            nextKey = nextKeyOf(entity)
            if (nextKey != null && nextKey > page) {
                empties++
                page = nextKey
            } else {
                // no next page (or end-of-pool 400 → null entity): return the
                // empty result as-is
                nextKey = nextKeyOf(entity)?.takeIf { it > page }
                empties = maxHops + 1
            }
        }
    } while (gifs.isEmpty() && empties <= maxHops)
    return WalkedPage(gifs, nextKey)
}

/**
 * Reads ONLY Room (offline-first). Page rows carry ordered gif ids; `IN` queries
 * lose ordering, so results are re-ordered here.
 *
 * Custom PagingSources get NO automatic Room invalidation — the FACTORY
 * (FeedRepository.cachedPager) subscribes each instance to the invalidation
 * tracker for the PREF tables only: their changes are read-time filters that
 * must re-apply instantly (leak-zero). The registration lives with the factory
 * so this class stays free of the Room database handle (unit-testable); each
 * instance owns its observer, and an invalidation rebuilds via the factory —
 * same semantics as the observer living in init.
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
    private val contentPrefsDao: com.rjbiermann.giffyviewer.core.database.ContentPrefsDao,
    private val customFeedDao: com.rjbiermann.giffyviewer.core.database.CustomFeedDao,
    private val feed: FeedSource,
    private val pageDao: com.rjbiermann.giffyviewer.core.database.FeedPageDao,
    private val pageSize: Int,
    private val contentFilter: com.rjbiermann.giffyviewer.core.database.ContentFilter,
    private val forYouContext: suspend () -> com.rjbiermann.giffyviewer.feature.feed.FeedRepository.ForYouContext =
        {
            com.rjbiermann.giffyviewer.feature.feed.FeedRepository
                .ForYouContext("all", emptySet(), emptySet())
        },
    private val fetcher: FeedPageFetcher? = null,
    private val orientation: suspend () -> String = { "any" },
    private val verifiedOnly: suspend () -> Boolean = { false },
    private val prefs: suspend () -> com.rjbiermann.giffyviewer.core.datastore.FeedPrefs =
        {
            com.rjbiermann.giffyviewer.core.datastore
                .FeedPrefs()
        },
) : PagingSource<Int, Gif>() {
    /** Per-PAGER session dedup (SLICE-15 semantics, same as the network-live
     *  sources): a fresh pager generation builds a fresh source instance, so
     *  the fresh feed starts clean — a pull-to-refresh (or any generation
     *  restart) re-renders the refetched content even when every id is
     *  unchanged. Within one generation the set still accumulates across the
     *  pages this instance loads, covering the server-reshuffle re-listing
     *  that the DB-row dedup can't see (duplicate grid keys crash). */
    private val sessionSeen = HashSet<String>()

    override suspend fun load(params: LoadParams<Int>): PagingSource.LoadResult<Int, Gif> =
        try {
            val prefs = prefs()
            // Per-feed orientation "" = follow the global §6 pref.
            val orientation = prefs.orientation.ifEmpty { orientation() }
            val walked =
                walkFeedPages(
                    startPage = params.key ?: 1,
                    maxHops = MAX_WALK_HOPS,
                    pageFor = { walkFill(feed, pageDao, fetcher, it) },
                    nextKeyOf = { entity -> entity?.nextPageKey?.let { pageNumber(it) } },
                ) { page, entity -> pageGifs(page, entity, prefs, orientation) }
            PagingSource.LoadResult.Page(
                data = walked.gifs,
                prevKey = if (params.key ?: 1 == 1) null else (params.key ?: 1) - 1,
                nextKey = walked.nextKey,
            )
        } catch (t: Throwable) {
            PagingSource.LoadResult.Error(t)
        }

    /** One walk iteration's full read chain (moved verbatim from the old inline
     *  loop body; [page] drives the DB-row dedup for upstream pagination
     *  overlap). */
    private suspend fun pageGifs(
        page: Int,
        entity: com.rjbiermann.giffyviewer.core.database.FeedPageEntity?,
        prefs: com.rjbiermann.giffyviewer.core.datastore.FeedPrefs,
        orientation: String,
    ): List<Gif> {
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
        // never reach the UI, however they got into the cache. BLOCKED feed
        // refs ride the same reload (§6 stage 2, merged custom feeds).
        contentFilter.refreshFrom(contentPrefsDao)
        contentFilter.refreshBlockedFeeds(customFeedDao)
        // Favorites feed keeps unfavorited rows out at read time (instant
        // un-favorite; the round-robin cache itself refreshes on TTL).
        val scopeCtx = if (feed is FeedSource.ForYou) forYouContext() else null
        val favs =
            if (feed is FeedSource.Favorites) {
                contentPrefsDao.favoriteCreators().mapTo(HashSet()) { it.lowercase() }
            } else {
                null
            }
        val models =
            ids
                .filterNot { it in sessionSeen }
                .mapNotNull { byId[it]?.toModel() }
        // hide-count increment lives in the pipeline (PLAN §6): one batched
        // write per page load — recount on reload is accepted (read-time filter)
        val hidden = models.count { !contentFilter.allow(it.userName, it.tags, it.description) }
        if (hidden > 0) {
            contentPrefsDao.addHideCount(weekStartMs(System.currentTimeMillis()), hidden)
        }
        val verifiedOnly = verifiedOnly()
        return models
            .filter { contentFilter.allow(it.userName, it.tags, it.description) }
            // Verified-only (2026-10): read-time pref AFTER the block
            // filter — NOT inside allow() above, or unverified rows
            // would inflate the "hidden this week" block counter.
            .filter { !verifiedOnly || it.verified }
            .filter { favs == null || it.userName.lowercase() in favs }
            // §8 range chips: client-side, read-time, AFTER the filter;
            // not counted in hide counts — prefs, not blocks.
            .filter { it.matchesOrientation(orientation) }
            .filter { untagged(it, feed, prefs) }
            .filter { durationIn(it.durationSeconds, prefs.duration) }
            .filter { it.resolutionMatches(prefs.resolution) }
            // §8 shuffle: deterministic per-seed order (shuffleOrdered).
            .let { shuffleOrdered(it, prefs.shuffleSeed) }
            .onEach { sessionSeen.add(it.id) }
            // For You scope (§7 Creators·Niches·All): read-time filter over
            // the SAME cached server pages — one fetch, three filters.
            .filter {
                when (scopeCtx?.scope) {
                    "creators" -> it.userName.lowercase() in scopeCtx!!.followed
                    "niches" -> it.tags.any { t -> t.lowercase() in scopeCtx!!.joinedTags }
                    else -> true
                }
            }
    }

    override fun getRefreshKey(state: PagingState<Int, Gif>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}

/** §8 duration chip → seconds bounds (null = unbounded). */
internal fun durationIn(
    seconds: Double,
    chip: String,
): Boolean {
    val d = seconds
    return when (chip) {
        "lt10" -> d < 10
        "10-30" -> d >= 10 && d < 30
        "30-60" -> d >= 30 && d < 60
        "1-5m" -> d >= 60 && d < 300
        "gt5m" -> d >= 300
        else -> true
    }
}
