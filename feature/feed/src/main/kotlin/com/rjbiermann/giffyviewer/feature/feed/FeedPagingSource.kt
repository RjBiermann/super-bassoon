package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
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
    private val fetcher: FeedPageFetcher? = null,
    private val orientation: suspend () -> String = { "any" },
    private val verifiedOnly: suspend () -> Boolean = { false },
    private val prefs: suspend () -> com.rjbiermann.giffyviewer.core.datastore.FeedPrefs =
        {
            com.rjbiermann.giffyviewer.core.datastore
                .FeedPrefs()
        },
) : PagingSource<Int, Gif>() {
    init {
        db.invalidationTracker.addObserver(
            object : androidx.room.InvalidationTracker.Observer(
                "creator_prefs",
                "tag_prefs",
                "keyword_blocks",
                "custom_feeds",
            ) {
                override fun onInvalidated(tables: Set<String>) {
                    invalidate()
                }
            },
        )
    }

    override suspend fun load(params: LoadParams<Int>): PagingSource.LoadResult<Int, Gif> {
        var page = params.key ?: 1
        return try {
            val prefs = prefs()
            // Per-feed orientation "" = follow the global §6 pref.
            val orientation = prefs.orientation.ifEmpty { orientation() }
            var gifs: List<Gif> = emptyList()
            var nextKey: Int? = null
            // §8 filter dead-end fix (live-proven): a strict client filter can
            // empty EVERY cached page; with 0 visible items nothing scrolls →
            // no APPEND → the feed starves forever. Fetch consecutive unfetched
            // pages through the empties (bounded: rate-limit safe, ≤10/5s).
            var empties = 0
            do {
                var entity = pageDao.page(feedPageKey(feed, page))
                // Unfetched page (the cache lags the scroll): fill it HERE — Paging
                // consults the mediator only when the source's data is exhausted, so
                // a fast scroll used to reach an unfetched page and either stall the
                // feed (null next = end-of-list) or drain empty pages (user report).
                // Same fetcher the mediator uses → one API call, rate-limit safe.
                if (entity == null) {
                    fetcher?.fill(page)
                    entity = pageDao.page(feedPageKey(feed, page))
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
                // never reach the UI, however they got into the cache. BLOCKED feed
                // refs ride the same reload (§6 stage 2, merged custom feeds).
                contentFilter.refreshFrom(db.contentPrefsDao())
                contentFilter.refreshBlockedFeeds(db.customFeedDao())
                // Favorites feed keeps unfavorited rows out at read time (instant
                // un-favorite; the round-robin cache itself refreshes on TTL).
                val scopeCtx = if (feed is FeedSource.ForYou) forYouContext() else null
                val favs =
                    if (feed is FeedSource.Favorites) {
                        db.contentPrefsDao().favoriteCreators().mapTo(HashSet()) { it.lowercase() }
                    } else {
                        null
                    }
                // Session dedup (repository-level): covers ids still live in the
                // pager's list even when their source row was rewritten (server
                // reshuffles) — duplicate grid keys crash the measure pass.
                val sessionSeen =
                    com.rjbiermann.giffyviewer.feature.feed.FeedRepository
                        .seenFor(feed.keyBase)
                val models =
                    ids
                        .filterNot { it in sessionSeen }
                        .mapNotNull { byId[it]?.toModel() }
                // hide-count increment lives in the pipeline (PLAN §6): one batched
                // write per page load — recount on reload is accepted (read-time filter)
                val hidden = models.count { !contentFilter.allow(it.userName, it.tags, it.description) }
                if (hidden > 0) {
                    db.contentPrefsDao().addHideCount(weekStartMs(System.currentTimeMillis()), hidden)
                }
                val verifiedOnly = verifiedOnly()
                val pageGifs =
                    models
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
                // loop tail: consume empty pages with a next key (≤3 consecutive
                // — rate-limit safety), otherwise return what we have.
                if (pageGifs.isNotEmpty()) {
                    gifs = pageGifs
                    nextKey = entity?.nextPageKey?.let { pageNumber(it) }
                } else {
                    nextKey = entity?.nextPageKey?.let { pageNumber(it) }
                    if (nextKey != null && nextKey > page) {
                        empties++
                        page = nextKey
                    } else {
                        // no next page: return the empty result as-is
                        nextKey = entity?.nextPageKey?.let { pageNumber(it) }?.takeIf { it > page }
                        empties = 99
                    }
                }
            } while (gifs.isEmpty() && empties <= 8)
            PagingSource.LoadResult.Page(
                data = gifs,
                prevKey = if (params.key ?: 1 == 1) null else (params.key ?: 1) - 1,
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
