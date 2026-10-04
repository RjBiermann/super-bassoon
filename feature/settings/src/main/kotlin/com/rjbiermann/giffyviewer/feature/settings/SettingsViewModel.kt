package com.rjbiermann.giffyviewer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjbiermann.giffyviewer.core.database.ContentPrefsBackup
import com.rjbiermann.giffyviewer.core.database.ContentPrefsDao
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.database.weekStartMs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Blocked lists (PLAN §6) — unblocking hits the invalidation tracker,
 *  so the feed re-filters and previously hidden tiles reappear. */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val db: GiffyDatabase,
        private val settings: com.rjbiermann.giffyviewer.core.datastore.SettingsRepository,
        // Favorites-cache invalidation on a favorites-set change (same
        // repository rule the quick-toggle uses — FeedRepository.evictFavoritesCache).
        private val feedRepository: com.rjbiermann.giffyviewer.feature.feed.FeedRepository,
    ) : ViewModel() {
        private val dao: ContentPrefsDao = db.contentPrefsDao()

        data class BlockedUi(
            val creators: List<String> = emptyList(),
            val tags: List<String> = emptyList(),
            val keywords: List<String> = emptyList(),
            val favorites: List<String> = emptyList(),
        )

        val blocked: StateFlow<BlockedUi> =
            combine(
                dao.blockedCreatorsFlow(),
                dao.blockedTagsFlow(),
                dao.blockedKeywordsFlow(),
                dao.favoriteCreatorsFlow(),
            ) { c, t, k, f -> BlockedUi(c, t, k, f) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockedUi())

        fun unblockCreator(username: String) {
            viewModelScope.launch { dao.unblockCreator(username) }
        }

        fun unfavoriteCreator(username: String) {
            viewModelScope.launch {
                dao.unblockCreator(username)
                // The favorites set changed — same invalidation as the quick toggle.
                feedRepository.evictFavoritesCache()
            }
        }

        fun unblockTag(tag: String) {
            viewModelScope.launch { dao.clearTag(tag) }
        }

        fun unblockKeyword(pattern: String) {
            viewModelScope.launch { dao.unblockKeyword(pattern) }
        }

        /** PLAN §6 Phase 6: optional PIN app lock. */
        val pinHash: StateFlow<String?> =
            settings.pinHash
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        fun setPin(pin: String) {
            viewModelScope.launch { settings.setPin(pin) }
        }

        fun removePin() {
            viewModelScope.launch { settings.clearPin() }
        }

        /** PLAN §6: single rolling 7-day hidden-item counter. */
        val hiddenThisWeek: StateFlow<Int> =
            dao
                .hideCount(weekStartMs(System.currentTimeMillis()))
                .map { it ?: 0 }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

        /** SD stream URLs on metered/slow connections (PLAN §5 data-saver). */
        val dataSaver: StateFlow<Boolean> =
            settings.dataSaver
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        fun setDataSaver(enabled: Boolean) {
            viewModelScope.launch { settings.setDataSaver(enabled) }
        }

        /** Verified-creators-only (2026-10): spam filter — read-time pref. */
        val verifiedOnly: StateFlow<Boolean> =
            settings.verifiedOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        fun setVerifiedOnly(enabled: Boolean) {
            viewModelScope.launch { settings.setVerifiedOnly(enabled) }
        }

        /** Orientation filter (§6): any | vertical | horizontal — read-time pref. */
        val orientationFilter: StateFlow<String> =
            settings.orientationFilter.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "any")

        fun setOrientationFilter(value: String) {
            viewModelScope.launch { settings.setOrientationFilter(value) }
        }

        /** Video fit (§5): fit | crop | stretch, shared by both players. */
        val videoFit: StateFlow<String> =
            settings.videoFit.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "fit")

        fun setVideoFit(fit: String) {
            viewModelScope.launch { settings.setVideoFit(fit) }
        }

        /** Grid columns (PLAN §6 responsive-first): 0 = Auto (width-derived). */
        val gridColumns: StateFlow<Int> =
            settings.gridColumns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

        fun setGridColumns(columns: Int) {
            viewModelScope.launch { settings.setGridColumns(columns) }
        }

        /** Inline feed autoplay (app-only, AGENTS-PLAYER spec) — 1-col feed preview. */
        val feedAutoplay: StateFlow<Boolean> =
            settings.feedAutoplay.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

        fun setFeedAutoplay(enabled: Boolean) {
            viewModelScope.launch { settings.setFeedAutoplay(enabled) }
        }

        /** TV home row size (slice 11): default | large | xl — TV-visible row. */
        val tvRowHeight: StateFlow<String> =
            settings.tvRowHeight.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "default")

        fun setTvRowHeight(value: String) {
            viewModelScope.launch { settings.setTvRowHeight(value) }
        }

        /** PLAN §9 theme options. */
        val amoled: StateFlow<Boolean> =
            settings.amoled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val dynamicColor: StateFlow<Boolean> =
            settings.dynamicColor
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        fun setAmoled(enabled: Boolean) {
            viewModelScope.launch { settings.setAmoled(enabled) }
        }

        fun setDynamicColor(enabled: Boolean) {
            viewModelScope.launch { settings.setDynamicColor(enabled) }
        }

        /** Gate 7: export/import all content controls as JSON. */
        suspend fun exportJson(): String = ContentPrefsBackup.export(dao, settings.dataSaver.first(), db.customFeedDao().all().first())

        suspend fun importJson(json: String): Result<Int> =
            runCatching {
                val count = ContentPrefsBackup.import(dao, json, db.customFeedDao())
                settings.setDataSaver(ContentPrefsBackup.parseDataSaver(json))
                // Restored FAVORITED creator rows change the favorites set — the
                // same Favorites-cache invalidation as every other toggler.
                feedRepository.evictFavoritesCache()
                count
            }
    }
