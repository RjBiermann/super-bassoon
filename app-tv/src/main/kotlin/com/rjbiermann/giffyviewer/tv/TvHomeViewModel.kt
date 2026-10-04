package com.rjbiermann.giffyviewer.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.rjbiermann.giffyviewer.feature.feed.FeedRepository
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        val favorites = repository.paging(FeedSource.Favorites).cachedIn(viewModelScope)

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
