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
        db: GiffyDatabase,
        private val settings: com.rjbiermann.giffyviewer.core.datastore.SettingsRepository,
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
            viewModelScope.launch { dao.unblockCreator(username) }
        }

        fun unblockTag(tag: String) {
            viewModelScope.launch { dao.clearTag(tag) }
        }

        fun unblockKeyword(pattern: String) {
            viewModelScope.launch { dao.unblockKeyword(pattern) }
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
        suspend fun exportJson(): String = ContentPrefsBackup.export(dao, settings.dataSaver.first())

        suspend fun importJson(json: String): Result<Int> =
            runCatching {
                val count = ContentPrefsBackup.import(dao, json)
                settings.setDataSaver(ContentPrefsBackup.parseDataSaver(json))
                count
            }
    }
