package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.rjbiermann.giffyviewer.core.database.ContentFilter
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.network.GifsApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

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

        /** [scope] defaults to the live pref so the cached-path source's
         *  per-page loader keeps today's behavior while the network-live
         *  path hands in the re-keyed scope (#38). (Default resolved INSIDE —
         *  Kotlin forbids suspend calls in default parameter values.) */
        private suspend fun forYouContext(scope: String? = null): ForYouContext {
            val scope = scope ?: settings.forYouScope.firstOrNull() ?: "all"
            scopeCtx?.takeIf { isForYouMemoFresh(it, scope, now()) }?.let { return it }
            // Issue #30: a failed follows/niches fetch must NOT degrade to an
            // EMPTY scope filter — that dropped every row and rendered "Your
            // For You feed is empty" for the wrong reason (an auth hiccup read
            // exactly like a genuinely empty feed). The failure now propagates:
            // the paging sources surface it as a load error → the UI's error
            // state (offline retry stays available). The memo is written ONLY
            // on success — no cache-on-failure entry, so the next page load
            // retries the context immediately instead of honoring a 5-min
            // empty-shaped memo.
            val followed =
                if (scope == "creators") {
                    api.followedCreators().mapTo(HashSet()) { it.lowercase() }
                } else {
                    emptySet()
                }
            val joinedTags =
                if (scope == "niches") {
                    api
                        .followedNiches()
                        .niches
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
            if (feed is FeedSource.Liked) {
                // Network-live, never cached (PLAN §7); no RemoteMediator, no Room.
                kotlinx.coroutines.flow
                    .combine(
                        settings.orientationFilter,
                        settings.verifiedOnly,
                    ) { o, v -> o to v }
                    // DataStore re-emits the WHOLE Preferences map on any key
                    // write (user #26: mute flips restarted the pager — any
                    // unrelated pref write too). Dedupe on the combine EXIT
                    // (structural equality) so only a REAL filter move restarts.
                    .distinctUntilChanged()
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
                // #38 (2026-10 e2e: scope chips did nothing): the scope choice
                // re-keys the pager HERE — the repository choke point, so both
                // apps' rows refetch and no VM-side re-key duplicates it. The
                // 5-min context memo re-validates per request against THIS
                // scope (isForYouMemoFresh), so the fresh generation reads
                // freshly fetched follows, never a stale-scope memo.
                forYouPagingKeys(
                    settings.orientationFilter,
                    settings.verifiedOnly,
                    settings.forYouScope,
                ).flatMapLatest { (o, v, s) ->
                    Pager(
                        config = PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = 10, enablePlaceholders = false),
                        pagingSourceFactory = {
                            ForYouNetworkPagingSource(
                                api,
                                contentFilter,
                                PAGE_SIZE,
                                forYouContext = { forYouContext(s) },
                                orientation = o,
                                verifiedOnly = v,
                            )
                        },
                    ).flow
                }
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
                    // Same DataStore identity-emission dedupe as Liked (#26);
                    // FeedPrefs is a data class — structural equality holds.
                    .distinctUntilChanged()
                    .flatMapLatest { (o, v, p) ->
                        if (feed is FeedSource.Favorites && forceRefresh) {
                            // #5 shuffle-on-refresh: every forced generation
                            // (pull-to-refresh + the TV refresh control) re-rotates
                            // the round-robin and evicts pages cached under the old
                            // mapping — REFRESH re-fetches page 1 only, so untouched
                            // later pages would keep serving the old rotation
                            // until the TTL lapses.
                            reshuffleFavorites()
                        }
                        cachedPager(feed, forceRefresh, p, o, v)
                    }
            }

        /** #5 shuffle-on-refresh: a fresh volatile seed + eviction of pages
         *  cached under the old rotation. Never persisted — the seed dies with
         *  the process, so the first open after a restart is rotation-free:
         *  deterministic until the user refreshes, the requested semantics. */
        suspend fun reshuffleFavorites() {
            val next = java.util.Random(System.nanoTime()).nextLong()
            favoritesShuffleSeed = if (next == 0L) 1L else next
            db.feedPageDao().evictFavorites()
        }

        /** @Volatile so the pager builder and the shuffle-seed lambdas read the
         *  freshest rotation the moment a forced generation re-seeds it. */
        @Volatile
        private var favoritesShuffleSeed = 0L

        private fun cachedPager(
            feed: FeedSource,
            forceRefresh: Boolean,
            prefs: com.rjbiermann.giffyviewer.core.datastore.FeedPrefs,
            orientation: String,
            verifiedOnly: Boolean,
        ): Flow<PagingData<Gif>> {
            // LEAK H3 (2026-10 OOM investigation): every pager generation used
            // to add one Room InvalidationTracker.Observer with the tracker as
            // a PROCESS-LIFETIME registry and zero removal call sites — each
            // observer pinned its whole FeedPagingSource (DAOs, ContentFilter,
            // the generation's dedup set) and kept the invalidation dispatch
            // growing on every DB write (Room replaceAll straw in the sweep). A
            // generation is replaced by INVALIDATION (a pref-table write → the
            // factory builds the next source for the still-collecting flow) or
            // by CANCELLATION of its collector (any re-key: orientation,
            // verifiedOnly, per-feed prefs, tab switch) — the lease removes the
            // observer on both paths, so the tracker's count returns to
            // baseline after every swap. Deliberately NOT watching feed_pages
            // (unchanged policy): every mediator write would invalidate every
            // live pager — an endless background fetch storm (seen live on TV).
            val lease =
                PrefObservationLease(
                    tables =
                        listOf(
                            "creator_prefs",
                            "tag_prefs",
                            "keyword_blocks",
                            "custom_feeds",
                        ),
                    addObserver = { observer -> db.invalidationTracker.addObserver(observer) },
                    removeObserver = { observer -> db.invalidationTracker.removeObserver(observer) },
                )
            val pager =
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
                            favoritesSeed = { favoritesShuffleSeed },
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
                                favoritesSeed = { favoritesShuffleSeed },
                            ),
                            orientation = { orientation },
                            verifiedOnly = { verifiedOnly },
                            prefs = { prefs },
                        ).also(lease::observe)
                    },
                )
            // The factory only runs while the pager collects (paging's flow
            // pipeline), so wrap the collection: when the generation's
            // collector is cancelled, the builder's finally runs on the
            // cancelled coroutine and releases the lease — removeObserver is
            // a plain non-suspending call, cancellation-safe.
            return kotlinx.coroutines.flow.flow {
                try {
                    emitAll(pager.flow)
                } finally {
                    lease.dispose()
                }
            }
        }

        companion object {
            const val PAGE_SIZE = 20
            internal const val CTX_TTL_MS = 5 * 60_000L

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

/** #38 (2026-10 e2e: For You scope chips did nothing — the scope pref never
 *  re-keyed the pager): the ForYou paging-generation keys are the THREE
 *  settings that change its read-time behavior — the orientation pref,
 *  verified-only, and the §7 scope choice. Changing ANY of them must build
 *  a fresh pager generation; unrelated DataStore identity re-emissions
 *  collapse in the same dedupe-on-exit move the Liked pair uses (structural
 *  equality; dedupe BEFORE consumption — the batch-26 watcher lesson). The
 *  repository is the choke point on purpose: both apps' rows refetch through
 *  this one combine, no VM-side re-key on top. */
internal fun forYouPagingKeys(
    orientationFilter: kotlinx.coroutines.flow.Flow<String>,
    verifiedOnly: kotlinx.coroutines.flow.Flow<Boolean>,
    forYouScope: kotlinx.coroutines.flow.Flow<String>,
): kotlinx.coroutines.flow.Flow<Triple<String, Boolean, String>> =
    kotlinx.coroutines.flow
        .combine(
            orientationFilter,
            verifiedOnly,
            forYouScope,
        ) { o, v, s -> Triple(o, v, s) }
        .distinctUntilChanged()

/** #38 memo contract (pure, unit-tested): the 5-minute ForYou-context memo
 *  (rate-limit-safe per-page loads) only serves for the SAME scope within
 *  its TTL — a scope-chip switch invalidates it immediately even mid-TTL,
 *  so the refetch the re-key above triggers reads freshly fetched follows,
 *  never a stale-scope memo. Failures never write the memo (success-only
 *  store), so a failed fetch replays on the next page load. */
internal fun isForYouMemoFresh(
    ctx: FeedRepository.ForYouContext?,
    scope: String,
    atMs: Long,
): Boolean = ctx != null && ctx.scope == scope && atMs - ctx.at < FeedRepository.CTX_TTL_MS

/** Generation-scoped owner of one cached pager's Room invalidation
 *  observers (LEAK H3, see cachedPager): pairs every observer it adds with
 *  removal on BOTH end-of-generation paths — the source's own invalidation
 *  (pref-table write: the factory builds the next source for the
 *  still-collecting flow, so the old observer must go immediately) and the
 *  collector cancellation the wrapper's finally invokes dispose() for.
 *  [addObserver]/[removeObserver] are injected so the lifecycle rules are
 *  unit-testable without a Room database; they map 1:1 onto the tracker. */
internal class PrefObservationLease(
    private val tables: List<String>,
    private val addObserver: (androidx.room.InvalidationTracker.Observer) -> Unit,
    private val removeObserver: (androidx.room.InvalidationTracker.Observer) -> Unit,
) {
    /** Room-backed invalidation observer added at each add — removal is
     *  idempotent (Room map.remove), so cleanup order races are free. */
    private val tracked = java.util.concurrent.ConcurrentLinkedQueue<androidx.room.InvalidationTracker.Observer>()

    /** Registers [source]'s generation observer; tracking happens BEFORE the
     *  add so a cancellation racing this call can never strand an untracked
     *  observer. */
    fun observe(source: androidx.paging.PagingSource<Int, com.rjbiermann.giffyviewer.core.model.Gif>) {
        // Room's Observer has no pure-vararg constructor (first table is a
        // required positional param) — spread alone doesn't compile.
        val observerTables = tables.toTypedArray()
        val observer =
            object :
                androidx.room.InvalidationTracker.Observer(
                    observerTables[0],
                    *observerTables.copyOfRange(1, observerTables.size),
                ) {
                override fun onInvalidated(tables: Set<String>) {
                    source.invalidate()
                }
            }
        tracked.add(observer)
        source.registerInvalidatedCallback {
            tracked.remove(observer)
            removeObserver(observer)
        }
        addObserver(observer)
    }

    /** End-of-generation cleanup: removes every still-tracked observer. */
    fun dispose() {
        while (true) {
            val observer = tracked.poll() ?: break
            removeObserver(observer)
        }
    }
}
