package com.rjbiermann.giffyviewer.feature.auth

import androidx.lifecycle.ViewModel
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Paste-token auth state (AGENTS-AUTH.md): save, reveal, sign out. */
@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val store: TokenStore,
    ) : ViewModel() {
        val token: StateFlow<String?> = store.token

        /** @return false when the paste isn't a plausible JWT. */
        fun save(raw: String): Boolean = store.save(raw)

        fun signOut() = store.clear()
    }
