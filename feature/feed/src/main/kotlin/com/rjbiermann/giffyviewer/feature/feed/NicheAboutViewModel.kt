package com.rjbiermann.giffyviewer.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjbiermann.giffyviewer.core.network.CreatorSearchItemDto
import com.rjbiermann.giffyviewer.core.network.FollowedNicheDto
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.NicheDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Niche About state (round-3 #3: VM idiom like the other list screens — the
 * remember-vars machine hid its failures once already). Detail failure =
 * full-screen error + retry; the two section fetches degrade to empty sections.
 */
@HiltViewModel
class NicheAboutViewModel
    @Inject
    constructor(
        private val api: GifsApi,
    ) : ViewModel() {
        private val _detail = MutableStateFlow<NicheDetail?>(null)
        val detail: StateFlow<NicheDetail?> = _detail

        private val _topCreators = MutableStateFlow<List<CreatorSearchItemDto>>(emptyList())
        val topCreators: StateFlow<List<CreatorSearchItemDto>> = _topCreators

        private val _related = MutableStateFlow<List<FollowedNicheDto>>(emptyList())
        val related: StateFlow<List<FollowedNicheDto>> = _related

        private val _detailFailed = MutableStateFlow(false)
        val detailFailed: StateFlow<Boolean> = _detailFailed

        /** Initial load + Retry — idempotent per niche open. */
        fun load(nicheId: String) {
            viewModelScope.launch {
                _detailFailed.value = false
                runCatching { api.nicheDetail(nicheId) }
                    .onSuccess { _detail.value = it.niche }
                    .onFailure { if (_detail.value == null) _detailFailed.value = true }
                runCatching { api.nicheTopCreators(nicheId) }.onSuccess { _topCreators.value = it.creators }
                runCatching { api.nicheRelated(nicheId) }.onSuccess { _related.value = it.niches }
            }
        }
    }
