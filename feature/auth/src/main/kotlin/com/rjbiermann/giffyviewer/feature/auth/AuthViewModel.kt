package com.rjbiermann.giffyviewer.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
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

        /** PKCE material for the in-flight WebView login, kept until consumed. */
        private var pkce: TokenStore.Pkce? = null

        fun currentPkce(): TokenStore.Pkce = pkce ?: TokenStore.newPkce().also { pkce = it }

        /** @return false when the paste isn't a plausible JWT. */
        fun save(raw: String): Boolean = store.save(raw)

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
