package com.rjbiermann.giffyviewer.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores the user's Kinde JWT under alias `giffy_auth` (PLAN §2 / AGENTS-AUTH.md).
 * The only surface allowed to reveal the raw token is the token-reveal UI; never
 * log it.
 *
 * ponytail: androidx.security-crypto 1.1.0 deprecates EncryptedSharedPreferences
 * (no replacement yet); revisit when Google ships its successor — for now it is
 * still the strongest storage option on the platform.
 */
class TokenStore(
    context: Context,
) {
    private val prefs: SharedPreferences =
        EncryptedSharedPreferences
            .create(
                context,
                PREFS_NAME,
                MasterKey
                    .Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

    private val mutableToken = MutableStateFlow(prefs.getString(KEY, null))

    /** Signed-in bearer token, null when anonymous. */
    val token: StateFlow<String?> = mutableToken.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == KEY) mutableToken.value = prefs.getString(KEY, null)
        }
    }

    /**
     * Saves a pasted token. Accepts only a plausible Kinde JWT (three
     * dot-separated base64url parts) — protects against fat-finger pastes.
     * Returns false and stores nothing on bad input.
     */
    fun save(raw: String): Boolean {
        val token = raw.trim()
        if (!looksLikeJwt(token)) return false
        prefs.edit().putString(KEY, token).apply()
        return true
    }

    fun tokenOrNull(): String? = mutableToken.value

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val PREFS_NAME = "giffy_auth"
        private const val KEY = "bearer_token"

        fun looksLikeJwt(token: String): Boolean {
            val parts = token.split('.')
            if (parts.size != 3) return false
            return parts.all { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
        }
    }
}
