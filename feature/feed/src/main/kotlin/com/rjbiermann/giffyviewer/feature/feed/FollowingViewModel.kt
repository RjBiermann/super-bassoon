package com.rjbiermann.giffyviewer.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import com.rjbiermann.giffyviewer.core.network.upstreamApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Following screen state (PLAN §7 — UI label mirrors the site's profile-menu
 * "Following"): creators I follow (rich objects from `GET /v2/me/following`)
 * + niches I joined (`GET /v2/niches/following`). Entries jump to the creator
 * feed / niche feed. Logged-out → the surface is hidden entirely (no dead tabs).
 */
@HiltViewModel
class FollowingViewModel
    @Inject
    constructor(
        private val api: upstreamApi,
        tokenStore: TokenStore,
    ) : ViewModel() {
        val loggedIn = tokenStore.tokenOrNull() != null

        private val _creators = MutableStateFlow<List<com.rjbiermann.giffyviewer.core.network.CreatorSearchItemDto>>(emptyList())
        val creators: StateFlow<List<com.rjbiermann.giffyviewer.core.network.CreatorSearchItemDto>> = _creators

        private val _niches = MutableStateFlow<List<com.rjbiermann.giffyviewer.core.network.FollowedNicheDto>>(emptyList())
        val niches: StateFlow<List<com.rjbiermann.giffyviewer.core.network.FollowedNicheDto>> = _niches

        fun refresh() {
            if (!loggedIn) return
            viewModelScope.launch {
                // 269 follows → 3 pages of 100; fetch all (read-only, rate-limited).
                var page = 1
                runCatching {
                    while (true) {
                        val dto = api.followingCreators(page = page)
                        _creators.value = _creators.value + dto.items
                        if (page >= dto.pages) break
                        page++
                    }
                }
                runCatching { api.followedNiches() }
                    .onSuccess { _niches.value = it.niches }
            }
        }
    }
