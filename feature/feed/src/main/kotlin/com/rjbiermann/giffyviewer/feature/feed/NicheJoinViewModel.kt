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

/** Pending-write bookkeeping (issue #47 device finding): the id is pending
 *  from toggle start until its write completes either way — repeat presses
 *  while the write is in flight must not stack duplicate subscriptions. */
internal fun pendingAfterStart(
    current: Set<String>,
    id: String,
): Set<String> = current + id

internal fun pendingResolved(
    current: Set<String>,
    id: String,
): Set<String> = current - id

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

        /** Writes currently in flight; hosts disable their buttons while set. */
        private val _pending = MutableStateFlow<Set<String>>(emptySet())
        val pending: StateFlow<Set<String>> = _pending

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
            // Optimistic flip welded to the pending-guard: the button is
            // disabled for the write's lifetime anyway, so showing the target
            // state immediately cannot be re-pressed into a double-write, and
            // the device-verified race class (write completes during heavy
            // feed paging → header slot sometimes recomposes stale) never
            // shows a wrong direction — failure rolls the flip back with the
            // honest error instead of an invisible success.
            val targetJoined = id !in _joined.value
            _joined.value = joinedAfter(_joined.value, id, joined = targetJoined)
            _pending.value = pendingAfterStart(_pending.value, id)
            viewModelScope.launch {
                runCatching {
                    if (targetJoined) api.subscribeNiche(nicheId = id) else api.unsubscribeNiche(nicheId = id)
                }.onFailure {
                    // The write did not happen — roll the optimistic flip back
                    // (remove if we added, restore if we removed) AND surface
                    // the honest error.
                    _joined.value = if (targetJoined) _joined.value - id else _joined.value + id
                    _error.value = JOIN_UPDATE_FAILED
                }.also {
                    _pending.value = pendingResolved(_pending.value, id)
                }
            }
        }
    }
