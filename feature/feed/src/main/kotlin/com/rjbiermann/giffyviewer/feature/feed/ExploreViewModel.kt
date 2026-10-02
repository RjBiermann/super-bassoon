package com.rjbiermann.giffyviewer.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjbiermann.giffyviewer.core.network.CreatorSearchItemDto
import com.rjbiermann.giffyviewer.core.network.GifsApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Explore screen state (round-3 #3: same idiom as NichesViewModel/FollowingViewModel
 * — the remember-vars machine drifted once already). Paginated verified-creator
 * list (`v2/creators/verified`, anonymous OK).
 */
@HiltViewModel
class ExploreViewModel
    @Inject
    constructor(
        private val api: GifsApi,
    ) : ViewModel() {
        private val _creators = MutableStateFlow<List<CreatorSearchItemDto>>(emptyList())
        val creators: StateFlow<List<CreatorSearchItemDto>> = _creators

        private val _endReached = MutableStateFlow(false)
        val endReached: StateFlow<Boolean> = _endReached

        private val _loadFailed = MutableStateFlow(false)
        val loadFailed: StateFlow<Boolean> = _loadFailed

        private var nextPage = 1
        private var loading = false

        /** Fetches the next page; the screen re-fires on (size, loadFailed) walks. */
        fun loadMore() {
            if (loading || nextPage <= 0 || _endReached.value) return
            loading = true
            viewModelScope.launch {
                val before = _creators.value.size
                _loadFailed.value = false
                runCatching { api.verifiedCreators(page = nextPage) }
                    .onSuccess { pageDto ->
                        // upstream pagination overlaps: page n re-lists page n-1 rows —
                        // dedup by username or the LazyColumn keys collide (crashed live).
                        _creators.value = (_creators.value + pageDto.creators).distinctBy { it.username }
                        nextPage++
                        // Server repeats the tail when exhausted and carries no page
                        // count — no growth means the list is complete.
                        if (_creators.value.size == before) _endReached.value = true
                    }.onFailure { _loadFailed.value = true }
                loading = false
            }
        }
    }
