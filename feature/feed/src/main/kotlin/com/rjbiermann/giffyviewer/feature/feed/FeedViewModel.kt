package com.rjbiermann.giffyviewer.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import com.rjbiermann.giffyviewer.core.database.CreatorPrefEntity
import com.rjbiermann.giffyviewer.core.database.KeywordBlockEntity
import com.rjbiermann.giffyviewer.core.database.LikedIdsEntity
import com.rjbiermann.giffyviewer.core.database.TagPrefEntity
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.network.upstreamApi
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
        private val api: upstreamApi,
        private val tokenStore: TokenStore,
    ) : ViewModel() {
        private val mutableSource = MutableStateFlow<FeedSource>(FeedSource.Trending)
        val source: StateFlow<FeedSource> = mutableSource.asStateFlow()

        /** Bumped by pull-to-refresh / the "Refresh feed" pill: restarts the pager
         *  (fresh REFRESH generation) with the one-shot TTL bypass (PLAN §9). */
        private val refreshGen = MutableStateFlow(0)

        /** The forced-refresh flag is consumed once per generation so a later
         *  source switch doesn't inherit the bypass (stale-while-revalidate holds). */
        private var consumedGen = -1

        val gifs: Flow<PagingData<Gif>> =
            mutableSource
                .combine(refreshGen) { feed, gen -> feed to gen }
                .flatMapLatest { (feed, gen) ->
                    flow {
                        val forced =
                            (gen > 0 && consumedGen != gen) || feed.keyBase in pendingForce.value
                        pendingForce.value = pendingForce.value - feed.keyBase
                        consumedGen = gen
                        emitAll(repository.paging(feed, forceRefresh = forced))
                    }
                }.cachedIn(viewModelScope)

        /** Pull-to-refresh / pill: bump the generation → fresh REFRESH, TTL bypassed. */
        fun forceRefresh() {
            refreshGen.value = refreshGen.value + 1
        }

        fun open(feed: FeedSource) {
            viewModelScope.launch {
                // Per-feed sort persistence (§8): a fresh source adopts its saved sort.
                val saved = settings.feedSort(feed.baseKey).first()
                val sortable = feed is FeedSource.Search || feed is FeedSource.Creator || feed is FeedSource.Niche
                val adopt = sortable && saved.isNotEmpty() && sourceIsUnsorted(feed)
                mutableSource.value = if (adopt) feed.withSort(saved) else feed
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

        /** Toggle: favorited → removed, otherwise upserted (PLAN §6 state machine). */
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

        /** Pinned niche tab entries "id|name" (PLAN §7 pin-to-tabs). */
        val pinnedNiches: StateFlow<Set<String>> =
            settings.pinnedNiches
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

        /** FAVORITED niche groups → pinned tabs (PLAN §7); BLOCKED feeds the filter. */
        val favoriteGroups: StateFlow<List<com.rjbiermann.giffyviewer.core.database.NicheGroupEntity>> =
            db.nicheGroupDao().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /** Pinned creator usernames — Home tabs (PLAN §7 pin-to-tabs), shared by both apps. */
        val pinnedCreators: StateFlow<Set<String>> =
            settings.pinnedCreators
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

        fun togglePinnedCreator(username: String) {
            viewModelScope.launch { settings.togglePinnedCreator(username) }
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
    }

private fun toLikedIds(gifId: String) = LikedIdsEntity(gifId = gifId, syncedAt = System.currentTimeMillis())
