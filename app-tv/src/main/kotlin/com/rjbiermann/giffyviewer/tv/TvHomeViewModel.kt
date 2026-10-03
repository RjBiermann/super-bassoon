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

        /** Logged-in surfaces (Liked/Following rows) show only with a token —
         *  reactive so in-app sign-in/out updates the home rows without a restart. */
        val isLoggedIn: StateFlow<Boolean> =
            tokenStore.token
                .map { it != null }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
