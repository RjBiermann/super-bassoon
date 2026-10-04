package com.rjbiermann.giffyviewer.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import com.rjbiermann.giffyviewer.core.database.CreatorPrefEntity
import com.rjbiermann.giffyviewer.core.database.KeywordBlockEntity
import com.rjbiermann.giffyviewer.core.database.LikedIdsEntity
import com.rjbiermann.giffyviewer.core.database.TagPrefEntity
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.network.CollectionDto
import com.rjbiermann.giffyviewer.core.network.CollectionGifBody
import com.rjbiermann.giffyviewer.core.network.FollowedNicheDto
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.NicheAddBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Debounce for pref-driven pager invalidations (§8, wedge fix A2): rapid
 *  selector taps collapse into ONE forced generation bump. */
private const val PREFS_INVALIDATE_DEBOUNCE_MS = 250L

/**
 * One feed at a time; switching feeds swaps the pager (PLAN §7 tabs come later,
 * when search/settings features land).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel
    @Inject
    constructor(
        private val repository: FeedRepository,
        private val db: com.rjbiermann.giffyviewer.core.database.GiffyDatabase,
        private val settings: com.rjbiermann.giffyviewer.core.datastore.SettingsRepository,
        private val api: GifsApi,
        private val tokenStore: TokenStore,
    ) : ViewModel() {
        /** Seeded at init (mobile For-You default slice, 2026-10): a valid
         *  stored token lands the app on For You like TV does; no token keeps
         *  Trending. Also guards the offline fallback below — a seeded For You
         *  that never loaded (network-live source, no Room cache) reverts so
         *  the cached Trending grid still renders (airplane-mode gate 3). */
        private val seededForYou = FeedRepository.defaultLandingSource(tokenStore.token.value != null)

        private val mutableSource = MutableStateFlow<FeedSource>(seededForYou)
        val source: StateFlow<FeedSource> = mutableSource.asStateFlow()

        /** True only after the user picks a feed — a seeded default failing to
         *  load then falls back to Trending instead of an offline dead screen. */
        private var userNavigated = false

        /** Refresh-error fallback: the network-live For You pager failed
         *  BEFORE any user navigation with no content rendered (still on the
         *  seeded default) — the app must show cached content offline, so
         *  revert to Trending (Room-backed, gate 3). FeedScreen observes
         *  loadState and calls this; manual opens set the navigation guard.
         *  [currentSourceError] is re-observed at call time (A2 stale-error
         *  guard): a latched Error from the previous pager must not act on a
         *  source swap — A1's reset-emit clears it within the same delivery,
         *  and this re-read skips the residual same-frame window. */
        fun notifyRefreshError(
            hasContent: Boolean,
            currentSourceError: () -> Boolean,
        ) {
            if (!currentSourceError()) return
            if (!FeedRepository.shouldRevertToTrending(source.value, userNavigated, hasContent)) return
            userNavigated = false
            mutableSource.value = FeedSource.Trending
            _canGoBack.value = false
        }

        /** Bumped by pull-to-refresh / the "Refresh feed" pill: restarts the pager
         *  (fresh REFRESH generation) with the one-shot TTL bypass (PLAN §9). */
        private val refreshGen = MutableStateFlow(0)

        /** The forced-refresh flag is consumed once per generation so a later
         *  source switch doesn't inherit the bypass (stale-while-revalidate holds). */
        private var consumedGen = -1

        /** Stale-grid fix (S3 report): drop the previous pager's rendered data
         *  FIRST on every (feed, gen) generation. Without this beat the
         *  LazyPagingItems collector keeps serving the last generation's tiles
         *  (and its latched refresh Error state) underneath the new tab until
         *  the new pager's first page lands. The explicit source LoadStates
         *  overwrite the presenter's state on the reset event, so a latched
         *  error cannot leak into the new generation either (A2). */
        private fun resetGenerationPagingData(): PagingData<Gif> =
            PagingData.empty(
                LoadStates(
                    refresh = LoadState.Loading,
                    append = LoadState.NotLoading(false),
                    prepend = LoadState.NotLoading(false),
                ),
            )

        val gifs: Flow<PagingData<Gif>> =
            mutableSource
                .combine(refreshGen) { feed, gen -> feed to gen }
                .flatMapLatest { (feed, gen) ->
                    flow {
                        val forced =
                            (gen > 0 && consumedGen != gen) || feed.keyBase in pendingForce.value
                        pendingForce.value = pendingForce.value - feed.keyBase
                        consumedGen = gen
                        emit(resetGenerationPagingData())
                        emitAll(repository.paging(feed, forceRefresh = forced))
                    }
                }.cachedIn(viewModelScope)

        /** Pull-to-refresh / pill: bump the generation → fresh REFRESH, TTL bypassed. */
        fun forceRefresh() {
            refreshGen.value = refreshGen.value + 1
        }

        /** One-level back (audit round 3): feeds opened from chips/More/hops set
         *  no overlay flag, so system back exited the app. open() remembers the
         *  prior source; FeedScreen's BackHandler pops it before back exits. */
        private var previousSource: FeedSource? = null

        private val _canGoBack = MutableStateFlow(false)
        val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

        fun back() {
            previousSource?.let { mutableSource.value = it }
            previousSource = null
            _canGoBack.value = false
        }

        fun open(feed: FeedSource) {
            viewModelScope.launch {
                // Per-feed sort persistence (§8): a fresh source adopts its saved sort.
                val saved = settings.feedSort(feed.baseKey).first()
                val sortable = feed is FeedSource.Search || feed is FeedSource.Creator || feed is FeedSource.Niche
                val adopt = sortable && saved.isNotEmpty() && sourceIsUnsorted(feed)
                val next = if (adopt) feed.withSort(saved) else feed
                if (next != mutableSource.value) {
                    previousSource = mutableSource.value
                    // Any explicit open (chip/More/hop) is user navigation —
                    // the seeded-default offline fallback no longer applies.
                    userNavigated = true
                }
                _canGoBack.value = next != mutableSource.value && previousSource != null
                mutableSource.value = next
            }
        }

        private fun sourceIsUnsorted(feed: FeedSource): Boolean =
            when (feed) {
                is FeedSource.Search -> feed.sort.isEmpty()
                is FeedSource.Creator -> feed.sort.isEmpty()
                is FeedSource.Niche -> feed.sort.isEmpty()
                else -> false
            }

        /** Server like state (PLAN §5): liked_ids is the read mirror. */
        val likedIds: StateFlow<Set<String>> =
            db
                .likedIdsDao()
                .allFlow()
                .map { rows -> rows.map { it.gifId }.toSet() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

        /** Logged in = a token exists (PLAN §2). */
        val isLoggedIn: StateFlow<Boolean> =
            tokenStore.token
                .map { it != null }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        /** Refreshed per use — the swipe player calls this on open (PLAN §5). */
        fun syncLikes() {
            viewModelScope.launch {
                if (tokenStore.tokenOrNull() == null) return@launch
                runCatching {
                    withContext(Dispatchers.IO) { api.likedIds() }
                }.onSuccess { ids ->
                    db.likedIdsDao().replaceAll(ids.map { toLikedIds(it) })
                }
            }
        }

        /** Optimistic flip + revert on network failure (PLAN §9 action rail). */
        fun toggleLike(gifId: String) {
            viewModelScope.launch {
                val dao = db.likedIdsDao()
                val liked = gifId in dao.allIds()
                if (liked) dao.clearById(gifId) else dao.upsert(toLikedIds(gifId))
                val networkResult =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            if (liked) api.unlikeGif(gifId) else api.likeGif(gifId)
                        }
                    }
                if (networkResult.isFailure) {
                    // revert to the pre-tap state
                    if (liked) dao.upsert(toLikedIds(gifId)) else dao.clearById(gifId)
                }
            }
        }

        fun refresh() {
            // Toggle forces flatMapLatest to re-run; RemoteMediator re-checks TTL/network.
            val current = mutableSource.value
            mutableSource.value = FeedSource.Trending
            mutableSource.value = current
        }

        /** Content controls (PLAN §6) — writes hit the invalidation tracker,
         *  which re-runs the FeedPagingSource filter immediately. */
        fun blockCreator(username: String) {
            viewModelScope.launch {
                db.contentPrefsDao().upsertCreator(
                    CreatorPrefEntity(username.lowercase(), "BLOCKED", System.currentTimeMillis()),
                )
            }
        }

        /** Toggle: favorited → removed, otherwise upserted (PLAN §6 state machine).
         *  Either direction changes the favorites SET → the Favorites feed's
         *  cached round-robin pages (built against the old set) are evicted so
         *  the next load refetches against the new one (2026-10 user report:
         *  favoriting a creator blanked the Favorites feed until TTL). */
        fun toggleFavoriteCreator(username: String) {
            viewModelScope.launch {
                val dao = db.contentPrefsDao()
                val u = username.lowercase()
                if (dao.creatorState(u).first() == "FAVORITED") {
                    dao.unblockCreator(u)
                } else {
                    dao.upsertCreator(
                        CreatorPrefEntity(u, "FAVORITED", System.currentTimeMillis()),
                    )
                }
                repository.evictFavoritesCache()
            }
        }

        /** null = no pref yet. Sheet label depends on it. */
        fun creatorState(username: String) = db.contentPrefsDao().creatorState(username.lowercase())

        /** null = no pref yet (PLAN §6 state machine). */
        fun tagState(tag: String) = db.contentPrefsDao().tagState(tag.lowercase())

        /** Toggle: favorited → removed, otherwise upserted (Groups groundwork). */
        fun toggleFavoriteTag(tag: String) {
            viewModelScope.launch {
                val dao = db.contentPrefsDao()
                val t = tag.lowercase()
                if (dao.tagState(t).first() == "FAVORITED") {
                    dao.unfavoriteTag(t)
                } else {
                    dao.upsertTag(
                        TagPrefEntity(t, "FAVORITED", System.currentTimeMillis()),
                    )
                }
            }
        }

        /** Custom feed definitions (PLAN §7 builder) — chips on the home row. */
        val customFeeds: StateFlow<List<com.rjbiermann.giffyviewer.core.database.CustomFeedEntity>> =
            db.customFeedDao().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /** Quick-sheet "add to custom feed" (PLAN §7): appends a ref, deduped. */
        fun addToCustomFeed(
            defId: Long,
            ref: String,
        ) {
            viewModelScope.launch {
                val def = db.customFeedDao().byId(defId) ?: return@launch
                val refs = def.sourcesJson.split(',').filter { it.isNotBlank() }
                if (ref in refs) return@launch
                // Appending shifts the round-robin mapping — evict cached pages
                // so the next open refetches the blend with all refs.
                db.feedPageDao().evictBase("custom:$defId")
                db
                    .customFeedDao()
                    .upsert(def.copy(sourcesJson = (refs + ref).joinToString(",")))
            }
        }

        /** Pinned niche tab entries "id|name" (PLAN §7 pin-to-tabs). */
        val pinnedNiches: StateFlow<Set<String>> =
            settings.pinnedNiches
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

        /** Add-to pane sources (live-probed writes, 2026-10): saved collections
         *  + joined niches; both token-gated reads, fetched on pane open. */
        private val _collections = MutableStateFlow<List<CollectionDto>>(emptyList())
        val collections: StateFlow<List<CollectionDto>> = _collections

        fun refreshCollections() {
            viewModelScope.launch {
                if (tokenStore.tokenOrNull() == null) return@launch
                runCatching { withContext(Dispatchers.IO) { api.meCollections() } }
                    .onSuccess { _collections.value = it.collections }
            }
        }

        private val _joinedNiches = MutableStateFlow<List<FollowedNicheDto>>(emptyList())
        val joinedNiches: StateFlow<List<FollowedNicheDto>> = _joinedNiches

        fun refreshJoinedNiches() {
            viewModelScope.launch {
                if (tokenStore.tokenOrNull() == null) return@launch
                runCatching { withContext(Dispatchers.IO) { api.followedNiches() } }
                    .onSuccess { _joinedNiches.value = it.niches }
            }
        }

        /** Anonymous users never see the follow row anywhere (a PUT would 401).
         *  Pairs with [refreshFollowedCreators] gating. */
        val loggedIn: Boolean
            get() = tokenStore.tokenOrNull() != null

        /** Creator follow state (server source of truth): GET v1/me/follows →
         *  username array (verified), lowercased — Gif.userName casing is not
         *  guaranteed to match the server list. */
        private val _followedCreators = MutableStateFlow<Set<String>>(emptySet())
        val followedCreators: StateFlow<Set<String>> = _followedCreators

        fun refreshFollowedCreators() {
            viewModelScope.launch {
                if (tokenStore.tokenOrNull() == null) return@launch
                runCatching { withContext(Dispatchers.IO) { api.followedCreators() } }
                    .onSuccess { _followedCreators.value = it.map(String::lowercase).toSet() }
            }
        }

        /** PUT v1/me/follows/{username} / DELETE → 204 (verified), JSON body
         *  {source, source_id, position}. Optimistic flip only on success —
         *  signed-out users never reach here (the row is hidden). */
        fun toggleFollowCreator(username: String) {
            viewModelScope.launch {
                if (tokenStore.tokenOrNull() == null) return@launch
                val wasFollowing = username.lowercase() in _followedCreators.value
                runCatching {
                    if (wasFollowing) {
                        api.unfollowCreator(username)
                    } else {
                        api.followCreator(username)
                    }
                }.onSuccess {
                    _followedCreators.value =
                        if (wasFollowing) _followedCreators.value - username.lowercase() else _followedCreators.value + username.lowercase()
                }
            }
        }

        /** POST v2/me/collections/{id}/gifs {gifId} → 204 (probed, reverted). */
        fun addToCollection(
            folderId: String,
            gifId: String,
        ) {
            viewModelScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { api.addToCollection(folderId, CollectionGifBody(gifId)) }
                }
            }
        }

        /** PUT v2/gifs/{id}/niches {nicheId} → 202 (probed, reverted). */
        fun addToNiche(
            gifId: String,
            nicheId: String,
        ) {
            viewModelScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { api.addToNiche(gifId, NicheAddBody(nicheId)) }
                }
            }
        }

        /** Creator stats header (v1/users/{username}, verified): cleared for
         *  non-creator sources; failure = no header (tiles carry the name). */
        private val _creatorStats = MutableStateFlow<com.rjbiermann.giffyviewer.core.network.UserStatsDto?>(null)
        val creatorStats: StateFlow<com.rjbiermann.giffyviewer.core.network.UserStatsDto?> = _creatorStats

        fun refreshCreatorStats(username: String?) {
            viewModelScope.launch {
                if (username == null) {
                    _creatorStats.value = null
                    return@launch
                }
                runCatching { withContext(Dispatchers.IO) { api.userStats(username) } }
                    .onSuccess { _creatorStats.value = it }
                    .onFailure { _creatorStats.value = null }
            }
        }

        /** For You scope (§7 Creators·Niches·All, logged-in; default All). */
        val forYouScope: StateFlow<String> =
            settings.forYouScope
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "all")

        fun setForYouScope(scope: String) {
            viewModelScope.launch {
                settings.setForYouScope(scope)
                // Read-time filter lives in the paging source — restart the
                // generation so pages re-read with the new scope.
                refreshGen.value = refreshGen.value + 1
            }
        }

        /** FAVORITED custom feeds → pinned tabs (PLAN §7; the merged niche-group
         *  role, DB v10). BLOCKED feeds drive the filter instead. */
        val favoritedFeeds: StateFlow<List<com.rjbiermann.giffyviewer.core.database.CustomFeedEntity>> =
            db.customFeedDao().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /** Pinned creator usernames — Home tabs (PLAN §7 pin-to-tabs), shared by both apps. */
        val pinnedCreators: StateFlow<Set<String>> =
            settings.pinnedCreators
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

        fun togglePinnedCreator(username: String) {
            viewModelScope.launch { settings.togglePinnedCreator(username) }
        }

        /** "Surprise me" (§8): refresh the random unwatched pool + open it as a
         *  feed source. Returns true when a pool landed (the host opens the
         *  surface; empty = nothing cached or everything watched). */
        fun surpriseMe(onReady: (SurpriseResult) -> Unit) {
            viewModelScope.launch {
                val result = repository.refreshSurprise()
                if (result == SurpriseResult.OK) open(FeedSource.Surprise)
                onReady(result)
            }
        }

        /** §8 per-feed filter prefs (duration/resolution/orientation). */
        fun feedPrefs(baseKey: String): Flow<com.rjbiermann.giffyviewer.core.datastore.FeedPrefs> = settings.feedPrefs(baseKey)

        /** Global §6 orientation filter — B3: the filtered-empty predicate on
         *  FeedScreen reads per-feed prefs only; this is their fallback
         *  (same merge semantics as FeedPagingSource's
         *  prefs.orientation.ifEmpty { orientation() }). */
        val orientationFilter: StateFlow<String> =
            settings.orientationFilter
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "any")

        fun setFeedPrefs(
            baseKey: String,
            prefs: com.rjbiermann.giffyviewer.core.datastore.FeedPrefs,
        ) {
            viewModelScope.launch { settings.setFeedPrefs(baseKey, prefs) }
        }

        /** Saved server sort for this feed's base (§8 per-feed persistence). */
        fun sortFor(baseKey: String): Flow<String> = settings.feedSort(baseKey)

        /** KeyBases owed a one-shot TTL bypass on their next fetch: sort changes
         *  are deliberate actions — the feed must update immediately, not serve
         *  a possibly-stale cached row until the TTL lapses (user report). */
        private val pendingForce = MutableStateFlow<Set<String>>(emptySet())

        /** Chip click: persist the sort + open the same feed under its sort key,
         *  forced fresh once. */
        fun setSort(
            source: FeedSource,
            sort: String,
        ) {
            viewModelScope.launch {
                settings.setFeedSort(source.baseKey, sort)
                pendingForce.value = pendingForce.value + source.baseKey
                open(source.withSort(sort))
            }
        }

        /** Verified-only (2026-10): drives the filtered-empty grid state. */
        val verifiedOnly: StateFlow<Boolean> =
            settings.verifiedOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        /** Inline feed autoplay (AGENTS-PLAYER spec): 1-col feed muted preview; data-saver forces off. */
        val feedAutoplay: StateFlow<Boolean> =
            settings.feedAutoplay.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

        val dataSaver: StateFlow<Boolean> =
            settings.dataSaver.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        /** Matching creators above search results (§7); tap opens their feed. */
        @kotlinx.coroutines.ExperimentalCoroutinesApi
        val creatorResults: StateFlow<List<com.rjbiermann.giffyviewer.core.network.CreatorSearchItemDto>> =
            mutableSource
                .map { it }
                .distinctUntilChanged()
                .mapLatest { source ->
                    if (source is FeedSource.Search) {
                        runCatching { api.creatorsSearch(source.query) }
                            .getOrDefault(
                                com.rjbiermann.giffyviewer.core.network
                                    .CreatorSearchPageDto(),
                            ).items
                    } else {
                        emptyList()
                    }
                }.catch { emit(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun blockTag(tag: String) {
            viewModelScope.launch {
                db.contentPrefsDao().upsertTag(
                    TagPrefEntity(tag.lowercase(), "BLOCKED", System.currentTimeMillis()),
                )
            }
        }

        fun blockKeyword(pattern: String) {
            viewModelScope.launch {
                db.contentPrefsDao().blockKeyword(
                    KeywordBlockEntity(pattern.lowercase(), System.currentTimeMillis()),
                )
            }
        }

        /** One-time coach mark for long-press blocking (hidden once seen). */
        val showBlockHint: StateFlow<Boolean> =
            settings.blockHintShown
                .map { shown -> !shown }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        fun dismissBlockHint() {
            viewModelScope.launch { settings.markBlockHintShown() }
        }

        /** Pref-driven pager invalidation (§8, wedge fix A2) — LAST init member:
         *  every property above must be initialized before this runs (the
         *  viewModelScope dispatch is Main.immediate, so the collection starts
         *  synchronously during construction).
         *
         *  A rendered grid must re-read its pages through the fresh filters.
         *  The repository's cached-pager combine already restarts on global
         *  orientation / verified-only / this feed's per-feed prefs — this is
         *  the VM-side guarantee for the device-verified wedge (a source-load
         *  error can latch the presenter into an eternal Loading state, from
         *  which only a new generation recovers): ONE forced generation per
         *  change. Per-source drop(1): the inner combine's first emission is
         *  the feed's initial pref state, not a change — bumping on it would
         *  force-refetch p1 on every feed open (request storm). Debounce
         *  collapses rapid selector taps into one generation.
         */
        init {
            viewModelScope.launch {
                source
                    .flatMapLatest { feed ->
                        val feedPrefs =
                            combine(
                                settings.orientationFilter,
                                settings.verifiedOnly,
                                settings.feedPrefs(feed.baseKey),
                            ) { o, v, p -> Triple(o, v, p) }
                        feedPrefs
                            .drop(1)
                            .distinctUntilChanged()
                            .debounce(PREFS_INVALIDATE_DEBOUNCE_MS)
                    }.collect { forceRefresh() }
            }
        }
    }

private fun toLikedIds(gifId: String) = LikedIdsEntity(gifId = gifId, syncedAt = System.currentTimeMillis())
