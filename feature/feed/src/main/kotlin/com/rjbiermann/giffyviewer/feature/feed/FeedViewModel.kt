package com.rjbiermann.giffyviewer.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.rjbiermann.giffyviewer.core.database.CreatorPrefEntity
import com.rjbiermann.giffyviewer.core.database.KeywordBlockEntity
import com.rjbiermann.giffyviewer.core.database.TagPrefEntity
import com.rjbiermann.giffyviewer.core.model.Gif
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    ) : ViewModel() {
        private val mutableSource = MutableStateFlow<FeedSource>(FeedSource.Trending)
        val source: StateFlow<FeedSource> = mutableSource.asStateFlow()

        val gifs: Flow<PagingData<Gif>> =
            mutableSource
                .flatMapLatest { repository.paging(it) }
                .cachedIn(viewModelScope)

        fun open(feed: FeedSource) {
            mutableSource.value = feed
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
