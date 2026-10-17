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

/** Site lingo (B2/NicheAboutScreen parity): Join Niche / Leave Niche. Public:
 *  the TV header host (app-tv) renders its own tv-material button with it. */
fun joinNicheLabel(joined: Boolean): String = if (joined) "Leave Niche" else "Join Niche"

/** Pure join-state flip; the VM applies it ONLY on a verified write. */
internal fun joinedAfter(
    current: Set<String>,
    id: String,
    joined: Boolean,
): Set<String> = if (joined) current + id else current - id

/** Honest write-failure copy: state stays put, the host surfaces this. */
internal const val JOIN_UPDATE_FAILED = "Couldn't update join — check your connection and retry"

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

        /** Last failed write (issue #47 slice 1): null = none; surfaced by the
         *  header hosts, cleared on the next attempt. */
        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error

        fun dismissError() {
            _error.value = null
        }

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
            _error.value = null
            viewModelScope.launch {
                runCatching {
                    if (id in _joined.value) api.unsubscribeNiche(nicheId = id) else api.subscribeNiche(nicheId = id)
                }.onSuccess {
                    _joined.value = joinedAfter(_joined.value, id, joined = id in _joined.value)
                }.onFailure {
                    // No optimistic flip on failure — the honest error surfaces
                    // instead (the write did not happen).
                    _error.value = JOIN_UPDATE_FAILED
                }
            }
        }
    }
