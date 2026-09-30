package com.rjbiermann.giffyviewer.tv

import androidx.lifecycle.ViewModel
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.database.toModel
import com.rjbiermann.giffyviewer.core.model.Gif
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Continue Watching row (PLAN §8 TV): partial watches, freshest first. */
@HiltViewModel
class ContinueWatchingViewModel
    @Inject
    constructor(
        db: GiffyDatabase,
    ) : ViewModel() {
        data class Entry(
            val gif: Gif,
            val positionMs: Long,
        )

        val entries: Flow<List<Entry>> =
            db.watchHistoryDao().continueWatching(limit = 20).map { rows ->
                rows.mapNotNull { row ->
                    db.gifDao().byId(row.gifId)?.let { Entry(it.toModel(), row.positionMs) }
                }
            }
    }
