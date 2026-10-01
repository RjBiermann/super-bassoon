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
import com.rjbiermann.giffyviewer.core.network.GifsApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

        /** "Surprise me" pool (§8) — session-only, regenerated per request. */
        private val surprisePool = MutableStateFlow<List<Gif>?>(null)

        suspend fun refreshSurprise(): Boolean {
            val orientation = settings.orientationFilter.first()
            contentFilter.refreshFrom(db.contentPrefsDao())
            contentFilter.refreshGroupTags(db.nicheGroupDao())
            val pool =
                db
                    .gifDao()
                    .randomUnwatched(PAGE_SIZE * 2)
                    .map { it.toModel() }
                    .filter { contentFilter.allow(it.userName, it.tags, it.description) }
                    .filter { it.matchesOrientation(orientation) }
            surprisePool.value = pool.ifEmpty { null }
            return pool.isNotEmpty()
        }

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
                settings.orientationFilter.flatMapLatest { orientation ->
                    Pager(
                        config = PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = 10, enablePlaceholders = false),
                        pagingSourceFactory = { LikedNetworkPagingSource(api, contentFilter, PAGE_SIZE, orientation) },
                    ).flow
                }
            } else {
                // Orientation + §8 per-feed pref changes restart the pager
                // (fresh generation re-reads cached pages through the filters).
                settings.feedPrefs(feed.baseKey).flatMapLatest { prefs ->
                    cachedPager(feed, forceRefresh, prefs)
                }
            }

        private fun cachedPager(
            feed: FeedSource,
            forceRefresh: Boolean,
            prefs: com.rjbiermann.giffyviewer.core.datastore.FeedPrefs,
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
                        db,
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
                    )
                },
            ).flow

        companion object {
            const val PAGE_SIZE = 20
            private const val CTX_TTL_MS = 5 * 60_000L

            // Session-wide ids ever returned per feed. The server reshuffles page
            // contents between fetches, so a DB-row-only dedup misses ids still
            // live in the pager's list → duplicate LazyGrid keys → crash
            // (live: "Key was already used" during scroll).
            private val seenIds = HashMap<String, MutableSet<String>>()

            fun seenFor(keyBase: String): MutableSet<String> = seenIds.getOrPut(keyBase) { mutableSetOf() }
        }
    }
