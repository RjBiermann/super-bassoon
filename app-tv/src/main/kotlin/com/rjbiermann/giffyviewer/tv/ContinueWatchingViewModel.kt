package com.rjbiermann.giffyviewer.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjbiermann.giffyviewer.core.database.ContentFilter
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.database.toModel
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.model.matchesOrientation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Continue Watching row (PLAN §8 TV): partial watches, freshest first.
 *  Leak-zero (PLAN §6): watch-history-derived surfaces run the ContentFilter —
 *  a creator blocked after watching must not resurface from history. */
@HiltViewModel
class ContinueWatchingViewModel
    @Inject
    constructor(
        private val db: GiffyDatabase,
        contentFilter: ContentFilter,
        private val settings: com.rjbiermann.giffyviewer.core.datastore.SettingsRepository,
    ) : ViewModel() {
        data class Entry(
            val gif: Gif,
            val positionMs: Long,
        )

        val entries: StateFlow<List<Entry>> =
            settings.orientationFilter
                .flatMapLatest { orientation ->
                    db
                        .watchHistoryDao()
                        .continueWatching(limit = 20)
                        .map { rows ->
                            contentFilter.refreshFrom(db.contentPrefsDao())
                            contentFilter.refreshGroupTags(db.nicheGroupDao())
                            rows.mapNotNull { row ->
                                db.gifDao().byId(row.gifId)?.let {
                                    val gif = it.toModel()
                                    // filtered rows drop silently; hide-count already
                                    // counted at watch time — recount would double-count.
                                    // Orientation pref rides the same read (a pref, not a block).
                                    if (contentFilter.allow(gif.userName, gif.tags, gif.description) &&
                                        gif.matchesOrientation(orientation)
                                    ) {
                                        Entry(gif, row.positionMs)
                                    } else {
                                        null
                                    }
                                }
                            }
                        }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }
