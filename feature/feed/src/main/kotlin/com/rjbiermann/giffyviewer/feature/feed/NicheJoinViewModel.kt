package com.rjbiermann.giffyviewer.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import com.rjbiermann.giffyviewer.core.network.GifsApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Niche join/leave state (PLAN §7/§9 — API word: subscription, UI word:
 * Join/Leave Niche). Server state is the source of truth: joined ids come
 * from `GET /v2/niches/following` (401 anon → logged-out hides the buttons);
 * writes are the verified 202-subscription calls.
 */
@HiltViewModel
class NicheJoinViewModel
    @Inject
    constructor(
        private val api: GifsApi,
        private val tokenStore: TokenStore,
    ) : ViewModel() {
        private val _joined = MutableStateFlow<Set<String>>(emptySet())
        val joined: StateFlow<Set<String>> = _joined

        val loggedIn: Boolean
            get() = tokenStore.tokenOrNull() != null

        fun refresh() {
            if (!loggedIn) return
            viewModelScope.launch {
                runCatching { api.followedNiches() }
                    .onSuccess { _joined.value = it.niches.mapTo(HashSet()) { n -> n.id } }
            }
        }

        fun toggle(id: String) {
            if (!loggedIn) return
            viewModelScope.launch {
                runCatching {
                    if (id in _joined.value) api.unsubscribeNiche(nicheId = id) else api.subscribeNiche(nicheId = id)
                }.onSuccess {
                    _joined.value =
                        if (id in _joined.value) _joined.value - id else _joined.value + id
                }
            }
        }
    }
