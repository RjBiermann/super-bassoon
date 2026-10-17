package com.rjbiermann.giffyviewer.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.feature.feed.FeedRepository
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** TV home: feed rows; Favorites row appears only when favorites exist. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TvHomeViewModel
    @Inject
    constructor(
        private val repository: FeedRepository,
        private val db: com.rjbiermann.giffyviewer.core.database.GiffyDatabase,
        tokenStore: com.rjbiermann.giffyviewer.core.auth.TokenStore,
        private val api: com.rjbiermann.giffyviewer.core.network.GifsApi,
        private val settings: com.rjbiermann.giffyviewer.core.datastore.SettingsRepository,
    ) : ViewModel() {
        val trending = repository.paging(FeedSource.Trending).cachedIn(viewModelScope)
        val topThisWeek = repository.paging(FeedSource.TopThisWeek).cachedIn(viewModelScope)

        /** Favorites row with a forced-refresh generation (#36c, TV-side panel
         *  "Refresh feed": the quick-actions plumbing exists; the row pager
         *  just lacked a generation). Same shape as FeedViewModel's
         *  combine+flatMapLatest: gen 0 = today's one-shot pager; a bumped gen
         *  emits the S3 reset (Loading state kills the old grid flash + latched
         *  error, see FeedViewModel §S3) and re-keys with forceRefresh=true so
         *  the repository re-rotates the round-robin and evicts stale pages.
         *  PendLeader: only the Favorites row wires a refresh — other home rows
         *  stay one-shot (no speculative config). */
        private val favoritesGen = MutableStateFlow(0)
        val favorites: Flow<PagingData<Gif>> =
            favoritesGen
                .flatMapLatest { gen -> favoritesPagingFlow(gen) }
                .cachedIn(viewModelScope)

        private fun favoritesPagingFlow(gen: Int): Flow<PagingData<Gif>> =
            if (gen > 0) {
                flow {
                    emit(resetGenerationPagingData())
                    emitAll(repository.paging(FeedSource.Favorites, forceRefresh = true))
                }
            } else {
                repository.paging(FeedSource.Favorites)
            }

        private fun resetGenerationPagingData(): PagingData<Gif> =
            PagingData.empty(
                LoadStates(
                    refresh = LoadState.Loading,
                    append = LoadState.NotLoading(false),
                    prepend = LoadState.NotLoading(false),
                ),
            )

        /** Panel "Refresh feed" for Favorites-row cards (#36c): bumps the gen →
         *  fresh REFRESH + new rotation seed (rate-limit-safe: one page-1
         *  fetch per bump, same as the mobile pull-to-refresh path). */
        fun refreshFavorites() {
            favoritesGen.value = favoritesGen.value + 1
        }

        /** Liked (PLAN §7): network-live, never cached — same source as mobile. */
        val liked = repository.paging(FeedSource.Liked).cachedIn(viewModelScope)

        /** For You (filed 2026-10 TV↔mobile parity gap): network-live server
         *  personalization, same flow shape as Liked — mobile-first home row
         *  order (verified sweep: For You first when logged in). */
        val forYou = repository.paging(FeedSource.ForYou).cachedIn(viewModelScope)

        /** SLICE-12 filtered-empty hint (user report): the old hint blamed
         *  "Settings → Orientation" unconditionally — wrong when the cause is a
         *  per-feed §8 pref (or when no filter is set at all and another drop
         *  emptied the row). These are the EFFECTIVE orientation per feed's base
         *  key (per-feed pref merged over the global §6 pref, same semantics as
         *  FeedPagingSource's read chain); the hint names the actual filter. */
        val trendingOrientation =
            repository
                .effectiveOrientation(FeedSource.Trending.keyBase)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "any")
        val topThisWeekOrientation =
            repository
                .effectiveOrientation(FeedSource.TopThisWeek.keyBase)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "any")
        val favoritesOrientation =
            repository
                .effectiveOrientation(FeedSource.Favorites.keyBase)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "any")
        val likedOrientation =
            repository
                .effectiveOrientation(FeedSource.Liked.keyBase)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "any")
        val forYouOrientation =
            repository
                .effectiveOrientation(FeedSource.ForYou.keyBase)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "any")

        /** Logged-in surfaces (Liked/Following rows) show only with a token —
         *  reactive so in-app sign-in/out updates the home rows without a restart. */
        val isLoggedIn: StateFlow<Boolean> =
            tokenStore.token
                .map { it != null }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        /** For You scope (§7 Creators·Niches·All) — the same settings.forYouScope
         *  pref the mobile FeedViewModel binds; no duplicate pref logic here.
         *  Paging reads it per page (repo's 5-min context memo — rate-limit
         *  invariant), so the chip row only needs to persist the choice. */
        val forYouScope: StateFlow<String> =
            settings.forYouScope
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "all")

        fun setForYouScope(scope: String) {
            viewModelScope.launch { settings.setForYouScope(scope) }
        }

        /** §5 video fit (issue #28): grid tiles honor it on TV too — "fit"
         *  leaves the cards byte-identical (the no-jump invariant), crop matches
         *  the poster→preview→player chain. */
        val videoFit: StateFlow<String> =
            settings.videoFit
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "fit")

        /** TV home row size (slice 11): default | large | xl — DataStore pref,
         *  mapped to the card height in TvHomeScreen.rowHeightFor(). TV-visible
         *  Settings row (showTvRowHeight) — the mobile grid-columns block is
         *  hidden here, so this shows on TV only. */
        val tvRowHeight: StateFlow<String> =
            settings.tvRowHeight
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "default")

        /** Explore row (§9: Top Creators) — first page of verified creators, anon OK.
         *  Failure surfaces (audit round 3: no silent blank strips). */
        private val _exploreFailed = kotlinx.coroutines.flow.MutableStateFlow(false)
        val exploreFailed = _exploreFailed.asStateFlow()
        val exploreCreators =
            kotlinx.coroutines.flow.MutableStateFlow<List<com.rjbiermann.giffyviewer.core.network.CreatorSearchItemDto>>(
                emptyList(),
            )

        init {
            refreshExplore()
        }

        fun refreshExplore() {
            viewModelScope.launch {
                _exploreFailed.value = false
                runCatching { api.verifiedCreators() }
                    .onSuccess { exploreCreators.value = it.creators }
                    .onFailure { _exploreFailed.value = true }
            }
        }

        val hasFavorites =
            db
                .contentPrefsDao()
                .favoriteCreatorsFlow()
                .map { it.isNotEmpty() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    }
