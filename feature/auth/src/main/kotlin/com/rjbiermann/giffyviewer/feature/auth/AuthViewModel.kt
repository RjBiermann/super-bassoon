package com.rjbiermann.giffyviewer.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sign-in state (AGENTS-AUTH.md): PKCE WebView login (primary), paste-token
 * fallback, reveal, sign out.
 */
@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val store: TokenStore,
    ) : ViewModel() {
        val token: StateFlow<String?> = store.token

        /** Signed-in @username — id_token `preferred_username` claim
         *  (live-verified 2026-10-02 against `v1/users/{username}`). */
        val username: StateFlow<String?> =
            store.token
                .map { t -> t?.let(TokenStore::usernameFromJwt) }
                .stateIn(
                    viewModelScope,
                    kotlinx.coroutines.flow.SharingStarted
                        .WhileSubscribed(5_000),
                    null,
                )

        /** PKCE material for the in-flight WebView login, kept until consumed. */
        private var pkce: TokenStore.Pkce? = null

        fun currentPkce(): TokenStore.Pkce = pkce ?: TokenStore.newPkce().also { pkce = it }

        /** @return false when the paste isn't a plausible JWT — or a full Kinde
         *  token bundle (JSON with id_token + refresh_token, PLAN §2): pasted
         *  sessions otherwise dead-end after the 1h ID-token expiry.
         *  Quote-tolerant: some paste paths strip quotes — regex-extract keys
         *  from both proper JSON and the bare {id_token:...,refresh_token:...} form. */
        fun save(raw: String): Boolean {
            val trimmed = raw.trim()
            if (trimmed.startsWith("{")) {
                fun key(k: String): String? = Regex("\"?$k\"?\\s*[:=]\\s*\"?([A-Za-z0-9_.-]+)").find(trimmed)?.groupValues?.get(1)
                val id = key("id_token")
                val refresh = key("refresh_token")
                return if (id != null) store.save(id, refresh) else false
            }
            return store.save(trimmed)
        }

        /** Exchanges the WebView-captured authorization code on IO. */
        fun exchangeCode(code: String) {
            val verifier = pkce?.verifier ?: return
            pkce = null
            viewModelScope.launch(Dispatchers.IO) {
                store.exchangeBlocking(code, verifier)
            }
        }

        /** PLAN §2: wipe token storage + WebView cookies (auth2/kinde session),
         *  so the next login starts clean instead of auto-reusing the session. */
        fun signOut() {
            store.clear()
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    android.webkit.CookieManager.getInstance().apply {
                        removeAllCookies(null)
                        removeSessionCookies(null)
                        flush()
                    }
                }
            }
        }
    }
