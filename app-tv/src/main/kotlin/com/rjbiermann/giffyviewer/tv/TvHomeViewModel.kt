package com.rjbiermann.giffyviewer.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.rjbiermann.giffyviewer.core.database.CreatorPrefEntity
import com.rjbiermann.giffyviewer.feature.feed.FeedRepository
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
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
        val settings: com.rjbiermann.giffyviewer.core.datastore.SettingsRepository,
        tokenStore: com.rjbiermann.giffyviewer.core.auth.TokenStore,
        private val api: com.rjbiermann.giffyviewer.core.network.GifsApi,
    ) : ViewModel() {
        val trending = repository.paging(FeedSource.Trending).cachedIn(viewModelScope)
        val topThisWeek = repository.paging(FeedSource.TopThisWeek).cachedIn(viewModelScope)
        val favorites = repository.paging(FeedSource.Favorites).cachedIn(viewModelScope)

        /** Liked (PLAN §7): network-live, never cached — same source as mobile. */
        val liked = repository.paging(FeedSource.Liked).cachedIn(viewModelScope)

        /** Logged-in surfaces (Liked/Following rows) show only with a token. */
        val isLoggedIn = tokenStore.tokenOrNull() != null

        /** Explore row (§9: Top Creators) — first page of verified creators, anon OK. */
        val exploreCreators =
            kotlinx.coroutines.flow.MutableStateFlow<List<com.rjbiermann.giffyviewer.core.network.CreatorSearchItemDto>>(
                emptyList(),
            )

        init {
            viewModelScope.launch {
                runCatching { api.verifiedCreators() }
                    .onSuccess { exploreCreators.value = it.creators }
            }
        }

        val hasFavorites =
            db
                .contentPrefsDao()
                .favoriteCreatorsFlow()
                .map { it.isNotEmpty() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        /** Pinned creators — top-row pills (PLAN §7 pin-to-tabs), shared with mobile. */
        val pinnedCreators =
            settings.pinnedCreators
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

        fun togglePinnedCreator(username: String) {
            viewModelScope.launch { settings.togglePinnedCreator(username) }
        }

        /** null = no pref yet; dialog label depends on it. Same rules as mobile. */
        fun creatorState(username: String) = db.contentPrefsDao().creatorState(username.lowercase())

        fun toggleFavoriteCreator(username: String) {
            viewModelScope.launch {
                val dao = db.contentPrefsDao()
                val u = username.lowercase()
                if (dao.creatorState(u).first() == "FAVORITED") {
                    dao.unblockCreator(u)
                } else {
                    dao.upsertCreator(CreatorPrefEntity(u, "FAVORITED", System.currentTimeMillis()))
                }
            }
        }

        fun blockCreator(username: String) {
            viewModelScope.launch {
                db.contentPrefsDao().upsertCreator(
                    CreatorPrefEntity(username.lowercase(), "BLOCKED", System.currentTimeMillis()),
                )
            }
        }
    }
