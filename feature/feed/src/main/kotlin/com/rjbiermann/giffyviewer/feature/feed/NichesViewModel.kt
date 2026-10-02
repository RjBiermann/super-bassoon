package com.rjbiermann.giffyviewer.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.NicheDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared niches browser state (mobile + TV — one paging path instead of the
 * TvNichesViewModel copy; ponytail audit 2026-10). Paginated taxonomy from
 * `v2/niches` (anonymous OK) with site-parity category filter + sort.
 */
@HiltViewModel
class NichesViewModel
    @Inject
    constructor(
        private val api: GifsApi,
        val settings: SettingsRepository,
    ) : ViewModel() {
        private val _niches = MutableStateFlow<List<NicheDto>>(emptyList())
        val niches: StateFlow<List<NicheDto>> = _niches

        private val _endReached = MutableStateFlow(false)
        val endReached: StateFlow<Boolean> = _endReached

        private val _loadFailed = MutableStateFlow(false)
        val loadFailed: StateFlow<Boolean> = _loadFailed

        private val _categories = MutableStateFlow<List<String>>(emptyList())
        val categories: StateFlow<List<String>> = _categories

        private val _category = MutableStateFlow<String?>(null)
        val category: StateFlow<String?> = _category

        private val _sort = MutableStateFlow("subscribers")
        val sort: StateFlow<String> = _sort

        private var nextPage = 1
        private var loading = false

        init {
            loadMore()
            viewModelScope.launch {
                runCatching { api.nicheCategories() }.onSuccess { _categories.value = it.categories }
            }
        }

        /** Fetches the next taxonomy page; safe to call repeatedly (tap/D-pad walk). */
        fun loadMore() {
            if (loading || nextPage <= 0) return
            loading = true
            viewModelScope.launch {
                runCatching { api.niches(page = nextPage, category = _category.value, order = _sort.value) }
                    .onSuccess { pageDto ->
                        _loadFailed.value = false
                        _niches.value = _niches.value + pageDto.niches
                        nextPage = if (pageDto.page < pageDto.pages) pageDto.page + 1 else 0
                        _endReached.value = nextPage == 0
                    }.onFailure { _loadFailed.value = true }
                loading = false
            }
        }

        fun selectCategory(category: String?) {
            if (_category.value == category) return
            _category.value = category
            restart()
        }

        fun resort(sort: String) {
            if (_sort.value == sort) return
            _sort.value = sort
            restart()
        }

        private fun restart() {
            nextPage = 1
            _niches.value = emptyList()
            _endReached.value = false
            _loadFailed.value = false
            loadMore()
        }

        fun isPinned(
            niche: NicheDto,
            pinned: Set<String>,
        ) = pinned.any { it.startsWith("${niche.id}|") }

        fun togglePin(niche: NicheDto) {
            viewModelScope.launch { settings.togglePinnedNiche(niche.id, niche.name) }
        }
    }
