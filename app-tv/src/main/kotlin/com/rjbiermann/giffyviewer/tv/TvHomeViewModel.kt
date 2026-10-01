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
        repository: FeedRepository,
        private val db: com.rjbiermann.giffyviewer.core.database.GiffyDatabase,
        val settings: com.rjbiermann.giffyviewer.core.datastore.SettingsRepository,
    ) : ViewModel() {
        val trending = repository.paging(FeedSource.Trending).cachedIn(viewModelScope)
        val topThisWeek = repository.paging(FeedSource.TopThisWeek).cachedIn(viewModelScope)
        val favorites = repository.paging(FeedSource.Favorites).cachedIn(viewModelScope)

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
