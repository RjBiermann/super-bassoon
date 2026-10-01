package com.rjbiermann.giffyviewer.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.database.SearchHistoryEntity
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.SuggestDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Search (PLAN §7): debounced typed autocomplete (every keystroke request
 * counts toward the ≤10/5s window, so the debounce + mapLatest cancel is
 * mandatory), Room history (cap 50, per-row remove + clear-all), and the
 * results feed = a standard filterable feed (FeedSource.Search, cache-first).
 * Creator results row refreshes per query, anonymous OK.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val api: GifsApi,
        private val db: GiffyDatabase,
        savedState: androidx.lifecycle.SavedStateHandle,
    ) : ViewModel() {
        /**
         * The live text field, restored across process death (gate: UI state
         * survives process death alongside login).
         */
        val query =
            MutableStateFlow(savedState.get<String>("query") ?: "").also { flow ->
                viewModelScope.launch {
                    flow.collect { savedState["query"] = it }
                }
            }

        /** Debounced autocomplete rows; empty query = no network (history only). */
        val suggestions: StateFlow<List<SuggestDto>> =
            query
                .asStateFlow()
                .map { it.trim() }
                .distinctUntilChanged()
                .debounce(SUGGEST_DEBOUNCE)
                .mapLatest { q ->
                    if (q.isEmpty()) {
                        emptyList()
                    } else {
                        api
                            .suggest(q)
                            .take(SUGGEST_LIMIT)
                    }
                }.catch { emit(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /** Recent searches, newest first (Room cap 50). */
        val history: StateFlow<List<SearchHistoryEntity>> =
            db
                .searchHistoryDao()
                .recent(HISTORY_CAP)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun setQuery(text: String) {
            query.value = text
        }

        /** Record (async) + hand the query to the caller (opens the results feed). */
        fun submit(raw: String): String? {
            val q = raw.trim()
            if (q.isEmpty()) return null
            viewModelScope.launch {
                db.searchHistoryDao().add(SearchHistoryEntity(query = q, searchedAt = System.currentTimeMillis()))
                db.searchHistoryDao().trim(HISTORY_CAP)
            }
            query.value = ""
            return q
        }

        fun removeHistory(entry: SearchHistoryEntity) {
            viewModelScope.launch { db.searchHistoryDao().remove(entry.query) }
        }

        fun clearHistory() {
            viewModelScope.launch { db.searchHistoryDao().clear() }
        }

        companion object {
            const val SUGGEST_DEBOUNCE = 300L
            const val SUGGEST_LIMIT = 8
            const val HISTORY_CAP = 50
        }
    }
