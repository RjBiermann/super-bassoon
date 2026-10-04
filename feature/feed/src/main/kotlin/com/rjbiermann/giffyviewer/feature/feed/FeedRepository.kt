package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.rjbiermann.giffyviewer.core.database.ContentFilter
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.database.toModel
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.model.matchesOrientation
import com.rjbiermann.giffyviewer.core.model.resolutionMatches
import com.rjbiermann.giffyviewer.core.network.GifsApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** "Surprise me" outcome (§8) — copy-differentiation on the feed's snackbar. */
enum class SurpriseResult { OK, EMPTY, FILTERED_EMPTY }

/**
 * Cache-first feed access (PLAN §5). UI reads Room via [FeedPagingSource];
 * [FeedMediator] refreshes in the background. Airplane mode: rows render from cache.
 */
@Singleton
@OptIn(ExperimentalPagingApi::class)
class FeedRepository
    @Inject
    constructor(
        private val api: GifsApi,
        private val db: GiffyDatabase,
        private val contentFilter: ContentFilter,
        private val settings: com.rjbiermann.giffyviewer.core.datastore.SettingsRepository,
    ) {
        /** For You scope context (§7): scope choice + followed creators + joined
         *  niche tags — server state, memoized 5 min so per-page loads don't
         *  multiply API calls (rate-limit invariant). */
        data class ForYouContext(
            val scope: String,
            val followed: Set<String>,
            val joinedTags: Set<String>,
            val at: Long = 0,
        )

        @Volatile
        private var scopeCtx: ForYouContext? = null

        private suspend fun forYouContext(): ForYouContext {
            val scope = settings.forYouScope.firstOrNull() ?: "all"
            scopeCtx?.takeIf { it.scope == scope && now() - it.at < CTX_TTL_MS }?.let { return it }
            val followed =
                if (scope == "creators") {
                    runCatching { api.followedCreators() }.getOrDefault(emptyList()).mapTo(HashSet()) { it.lowercase() }
                } else {
                    emptySet()
                }
            val joinedTags =
                if (scope == "niches") {
                    runCatching { api.followedNiches() }
                        .getOrDefault(
                            com.rjbiermann.giffyviewer.core.network
                                .FollowedNichesDto(),
                        ).niches
                        .flatMapTo(HashSet()) { n -> n.tags.map(String::lowercase) }
                } else {
                    emptySet()
                }
            return ForYouContext(scope, followed, joinedTags, now()).also { scopeCtx = it }
        }

        private fun now(): Long = System.currentTimeMillis()

        /** Favorites-cache invalidation (2026-10 user report): a favorites-set
         *  change (rename: toggle in quick actions, Settings unfavorite, backup
         *  import) must evict the cached round-robin pages — read-time filter +
         *  old mapping otherwise blank the Favorites feed until the 10-min TTL.
         *  Read-time-consistent refetch on next load; no background fetch
         *  (No-WorkManager rule). The session dedup set needs no clearing here:
         *  it is PER PAGER GENERATION now (FeedPagingSource instance field) and
         *  the creator_prefs write invalidates the source → the factory builds a
         *  fresh instance with a fresh set. */
        suspend fun evictFavoritesCache() {
            db.feedPageDao().evictFavorites()
        }

        /** "Surprise me" pool (§8) — session-only, regenerated per request. */
        private val surprisePool = MutableStateFlow<List<Gif>?>(null)

        /** "Surprise me" result (§8): OK = pool landed; FILTERED_EMPTY = cached
         *  rows existed but every candidate was dropped by ContentFilter /
         *  verified-only / orientation (the batch-14 all-portrait-pool shape —
         *  honest but mistitled as "nothing cached"); EMPTY = no unwatched
         *  rows at all (fresh install or everything watched). */
        suspend fun refreshSurprise(): SurpriseResult {
            val orientation = settings.orientationFilter.first()
            val verifiedOnly = settings.verifiedOnly.first()
            contentFilter.refreshFrom(db.contentPrefsDao())
            contentFilter.refreshBlockedFeeds(db.customFeedDao())
            val raw = db.gifDao().randomUnwatched(PAGE_SIZE * 2).map { it.toModel() }
            val pool =
                raw
                    .filter { contentFilter.allow(it.userName, it.tags, it.description) }
                    .filter { !verifiedOnly || it.verified }
                    .filter { it.matchesOrientation(orientation) }
            surprisePool.value = pool.ifEmpty { null }
            return when {
                pool.isNotEmpty() -> SurpriseResult.OK
                raw.isNotEmpty() -> SurpriseResult.FILTERED_EMPTY
                else -> SurpriseResult.EMPTY
            }
        }

        /** SLICE-12 hint sink: effective orientation for a feed's base key —
         *  the per-feed §8 pref merged over the global §6 pref (same semantics as
         *  FeedPagingSource's read chain / FeedScreen's B3 predicate). Composed
         *  per-feed so a filtered-empty hint can name the ACTUAL filter and
         *  value instead of wrongly blaming the global Settings (user report:
         *  a stale per-feed "horizontal" over a global "any" still blanks the
         *  row — global Settings → Orientation then shows unset). */
        fun effectiveOrientation(baseKey: String): Flow<String> =
            kotlinx.coroutines.flow.combine(
                settings.orientationFilter,
                settings.feedPrefs(baseKey),
            ) { global, perFeed -> perFeed.orientation.ifEmpty { global } }

        fun paging(
            feed: FeedSource,
            forceRefresh: Boolean = false,
        ): Flow<PagingData<Gif>> =
            if (feed is FeedSource.Surprise) {
                Pager(
                    config = PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = 10, enablePlaceholders = false),
                    pagingSourceFactory = { SurprisePoolPagingSource(surprisePool) },
                ).flow
            } else if (feed is FeedSource.Liked) {
                // Network-live, never cached (PLAN §7); no RemoteMediator, no Room.
                kotlinx.coroutines.flow
                    .combine(
                        settings.orientationFilter,
                        settings.verifiedOnly,
                    ) { o, v -> o to v }
                    .flatMapLatest { (o, v) ->
                        Pager(
                            config = PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = 10, enablePlaceholders = false),
                            pagingSourceFactory = {
                                LikedNetworkPagingSource(api, contentFilter, PAGE_SIZE, o, v)
                            },
                        ).flow
                    }
            } else if (feed is FeedSource.ForYou) {
                // Network-live, never cached (PLAN §7, same rule as Liked):
                // the server personalizes per user — Room cache would serve
                // another user's glob. ForYou context (scope + follows) is
                // fetched per page by the source (5-min repo memo makes
                // per-page calls cheap; rate-limit invariant).
                kotlinx.coroutines.flow
                    .combine(
                        settings.orientationFilter,
                        settings.verifiedOnly,
                    ) { o, v -> o to v }
                    .flatMapLatest { (o, v) ->
                        Pager(
                            config = PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = 10, enablePlaceholders = false),
                            pagingSourceFactory = {
                                ForYouNetworkPagingSource(
                                    api,
                                    contentFilter,
                                    PAGE_SIZE,
                                    forYouContext = { forYouContext() },
                                    orientation = o,
                                    verifiedOnly = v,
                                )
                            },
                        ).flow
                    }
            } else if (feed is FeedSource.Continue) {
                continueWatchingPager()
            } else {
                // Orientation (global) + §8 per-feed prefs BOTH restart the pager:
                // combine them so either change re-reads cached pages through the
                // fresh filters. The value must be HANDED to the source (captured
                // in a lambda) — the source's constructor defaults are no-ops
                // (live-proven 2026-10-01: prefs blob persisted but the grid ran
                // unfiltered because cachedPager dropped both params).
                kotlinx.coroutines.flow
                    .combine(
                        settings.orientationFilter,
                        settings.verifiedOnly,
                        settings.feedPrefs(feed.baseKey),
                    ) { o, v, p -> Triple(o, v, p) }
                    .flatMapLatest { (o, v, p) ->
                        cachedPager(feed, forceRefresh, p, o, v)
                    }
            }

        /** "Continue Watching" (§8, mobile): Room-only read through the SAME
         *  read-time chain TV's ContinueWatchingViewModel applies — ContentFilter
         *  (leak-zero) + verified-only + orientation + §8 per-feed prefs (the
         *  Filter ▾ dialog stays honest on this surface). DataStore prefs ride
         *  the combine → pager restart; Room-table changes (a block written on
         *  this surface, a finished video) ride the source's invalidation-tracker
         *  observer → per-load re-derivation. NO hide-count increment: the filter
         *  counted these at watch time — a recount would double-count (TV's
         *  ContinueWatchingViewModel comment). */
        private fun continueWatchingPager(): Flow<PagingData<Gif>> =
            kotlinx.coroutines.flow
                .combine(
                    settings.orientationFilter,
                    settings.verifiedOnly,
                    settings.feedPrefs(FeedSource.Continue.keyBase),
                ) { o, v, p -> Triple(o, v, p) }
                .flatMapLatest { (o, v, p) ->
                    Pager(
                        config = PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = 10, enablePlaceholders = false),
                        pagingSourceFactory = {
                            ContinueWatchingPagingSource(db) {
                                continueWatchingEntries(o, v, p)
                            }
                        },
                    ).flow
                }

        /** Derivation per load: watch_history → gifs, filtered read-time.
         *  Per-feed orientation "" = follow the global §6 pref (§8). */
        private suspend fun continueWatchingEntries(
            orientationPref: String,
            verifiedOnly: Boolean,
            prefs: com.rjbiermann.giffyviewer.core.datastore.FeedPrefs,
        ): List<Gif> {
            contentFilter.refreshFrom(db.contentPrefsDao())
            contentFilter.refreshBlockedFeeds(db.customFeedDao())
            val orientation = prefs.orientation.ifEmpty { orientationPref }
            val rows = db.watchHistoryDao().continueWatching(limit = CONTINUE_LIMIT).first()
            val byId =
                db
                    .gifDao()
                    .byIds(rows.map { it.gifId })
                    .associateBy { it.id }
            return rows
                .mapNotNull { row -> byId[row.gifId]?.toModel() }
                .filter { contentFilter.allow(it.userName, it.tags, it.description) }
                .filter { !verifiedOnly || it.verified }
                .filter { it.matchesOrientation(orientation) }
                .filter { durationIn(it.durationSeconds, prefs.duration) }
                .filter { it.resolutionMatches(prefs.resolution) }
                .let { shuffleOrdered(it, prefs.shuffleSeed) }
        }

        private fun cachedPager(
            feed: FeedSource,
            forceRefresh: Boolean,
            prefs: com.rjbiermann.giffyviewer.core.datastore.FeedPrefs,
            orientation: String,
            verifiedOnly: Boolean,
        ): Flow<PagingData<Gif>> =
            Pager(
                config = PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = 10, enablePlaceholders = false),
                initialKey = 1,
                remoteMediator =
                    FeedMediator(
                        feed = feed,
                        gifDao = db.gifDao(),
                        pageDao = db.feedPageDao(),
                        api = api,
                        pageSize = PAGE_SIZE,
                        force = forceRefresh,
                        favorites = { db.contentPrefsDao().favoriteCreators() },
                    ),
                pagingSourceFactory = {
                    FeedPagingSource(
                        db.contentPrefsDao(),
                        db.customFeedDao(),
                        feed,
                        db.feedPageDao(),
                        PAGE_SIZE,
                        contentFilter,
                        { forYouContext() },
                        // The source self-fills unfetched pages (fast-scroll race);
                        // same fetcher the mediator uses.
                        FeedPageFetcher(
                            feed,
                            db.gifDao(),
                            db.feedPageDao(),
                            api,
                            PAGE_SIZE,
                            favorites = { db.contentPrefsDao().favoriteCreators() },
                        ),
                        orientation = { orientation },
                        verifiedOnly = { verifiedOnly },
                        prefs = { prefs },
                    ).also(::observePrefTables)
                },
            ).flow

        /** Pref-table invalidation observer (moved from FeedPagingSource's init
         *  so the source class stays free of the Room database handle): each
         *  instance owns its observer; a pref-table write (block edit, custom
         *  feed state, favorites toggle) invalidates the live pager instantly
         *  (leak-zero re-filter). Deliberately NOT watching feed_pages: every
         *  mediator write would invalidate every live pager — an endless
         *  background fetch storm (seen live on TV). */
        private fun observePrefTables(source: androidx.paging.PagingSource<Int, com.rjbiermann.giffyviewer.core.model.Gif>) {
            db.invalidationTracker.addObserver(
                object : androidx.room.InvalidationTracker.Observer(
                    "creator_prefs",
                    "tag_prefs",
                    "keyword_blocks",
                    "custom_feeds",
                ) {
                    override fun onInvalidated(tables: Set<String>) {
                        source.invalidate()
                    }
                },
            )
        }

        companion object {
            const val PAGE_SIZE = 20
            private const val CTX_TTL_MS = 5 * 60_000L

            /** "Continue Watching" row length — TV's ContinueWatchingViewModel uses 20. */
            private const val CONTINUE_LIMIT = 20

            // Session-wide ids ever returned per feed — DELETED (2026-10, pull-to
            // -refresh blanking): the static map survived pager restarts, so a
            // forced REFRESH that re-listed the same ids (Favorites' deterministic
            // round-robin does, always) deduped every page to empty and the feed
            // rendered the "No favorites yet" state. Dedup is now PER PAGER
            // GENERATION (FeedPagingSource instance field) — same semantics the
            // network-live sources (Liked/ForYou) already use.

            /** Pure seed decision (mobile For-You default slice, 2026-10):
             *  a stored token cold-starts on For You (TV parity); anonymous
             *  keeps Trending. */
            fun defaultLandingSource(tokenPresent: Boolean): FeedSource = if (tokenPresent) FeedSource.ForYou else FeedSource.Trending

            /** Pure offline-fallback rule (same slice): only a SEEDED For You
             *  (never user-navigated, no content loaded) reverts to Trending.
             *  Trending rebuilds from Room — gate 3 holds offline; For You is
             *  network-live with no cache, so its error state is a dead end. */
            fun shouldRevertToTrending(
                source: FeedSource,
                userNavigated: Boolean,
                hasContent: Boolean,
            ): Boolean = source is FeedSource.ForYou && !userNavigated && !hasContent
        }
    }
