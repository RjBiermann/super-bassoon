package com.rjbiermann.giffyviewer.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
    fun save(
        raw: String,
        refreshToken: String? = null,
    ): Boolean {
        val token = raw.trim()
        if (!looksLikeJwt(token)) return false
        prefs
            .edit()
            .putString(KEY, token)
            .putString(REFRESH_KEY, refreshToken?.trim()?.takeIf { it.isNotEmpty() })
            .apply()
        return true
    }

    fun tokenOrNull(): String? = mutableToken.value

    fun refreshTokenOrNull(): String? = prefs.getString(REFRESH_KEY, null)

    fun clear() {
        prefs
            .edit()
            .remove(KEY)
            .remove(REFRESH_KEY)
            .apply()
    }

    /**
     * Blocking refresh of the user token (PLAN §2: refresh grant verified, public
     * SPA client, no secret). Called on a background dispatcher by the 401 handler.
     * Mints a fresh ID token from the stored refresh_token and stores it. Returns
     * false (and keeps the old token) when there is no refresh token or the grant
     * fails — caller falls back to anonymous session.
     */
    fun refreshBlocking(): Boolean {
        val refreshToken = prefs.getString(REFRESH_KEY, null) ?: return false
        val bundle = tokenBundle(refreshToken = refreshToken) ?: return false
        return save(bundle.idToken, bundle.refreshToken ?: refreshToken)
    }

    /**
     * Blocking OAuth authorization-code exchange (PKCE, verified live 2026-09-30:
     * `POST /oauth2/token` grant_type=authorization_code → id_token + refresh_token,
     * public client, no secret). Called on a background dispatcher by the login flow.
     * Stores both tokens and returns true on success.
     */
    fun exchangeBlocking(
        code: String,
        verifier: String,
    ): Boolean {
        val bundle = tokenBundle(code = code, verifier = verifier) ?: return false
        return save(bundle.idToken, bundle.refreshToken)
    }

    /** Token bundle from the authorization-code / refresh grants. */
    data class TokenBundle(
        val idToken: String,
        val refreshToken: String?,
    )

    /** PKCE pair + state for one login attempt. */
    data class Pkce(
        val verifier: String,
        val challenge: String,
        val state: String,
    )

    companion object {
        private const val PREFS_NAME = "giffy_auth"
        private const val KEY = "bearer_token"
        private const val REFRESH_KEY = "refresh_token"

        /** Public SPA client (azp claim of the Kinde token bundle — PLAN §2). */
        const val OAUTH_CLIENT_ID = "e06c34dac7654821bcb37e0393b54350"
        val OAUTH_TOKEN_URL: String get() = com.rjbiermann.giffyviewer.core.model.Hosts.oauthToken
        val OAUTH_REDIRECT_URI: String get() = com.rjbiermann.giffyviewer.core.model.Hosts.site

        /** S256 PKCE material for the authorize request. */
        fun newPkce(): Pkce {
            val bytes = ByteArray(64)
            java.security.SecureRandom().nextBytes(bytes)
            val verifier = bytes.joinToString("") { "%02x".format(it) }
            val digest =
                java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest(verifier.toByteArray(Charsets.US_ASCII))
            val challenge =
                java.util.Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest)
            return Pkce(verifier, challenge, verifier.take(16))
        }

        /**
         * Live-verified authorize request (2026-09-30): 302s to the redirect_uri
         * with `code` + `scope=… offline` (offline scope is what yields the
         * refresh token). Login form shown there when not signed in.
         */
        fun authorizeUrl(pkce: Pkce): String =
            com.rjbiermann.giffyviewer.core.model.Hosts.oauthAuthorize +
                "?client_id=$OAUTH_CLIENT_ID" +
                "&redirect_uri=${URLEncoder.encode(OAUTH_REDIRECT_URI, "UTF-8")}" +
                "&response_type=code" +
                "&scope=${URLEncoder.encode("openid profile email offline", "UTF-8")}" +
                "&code_challenge=${pkce.challenge}" +
                "&code_challenge_method=S256" +
                "&state=${pkce.state}"

        /**
         * Extracts the authorization code from a redirect URL hit. Returns null
         * when the URL is not our redirect (or the state doesn't match).
         */
        fun extractCode(
            url: String,
            expectedState: String,
        ): String? {
            if (!url.startsWith(OAUTH_REDIRECT_URI)) return null
            val params =
                url.substringAfter('?').split('&').associate {
                    it.substringBefore('=') to it.substringAfter('=', "")
                }
            if (params["state"] != expectedState) return null
            return params["code"]?.takeIf { it.isNotEmpty() }
        }

        fun looksLikeJwt(token: String): Boolean {
            val parts = token.split('.')
            if (parts.size != 3) return false
            return parts.all { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
        }

        /** Parses the refresh-grant response → new ID token, or null. */
        fun parseRefreshResponse(body: String): String? = parseTokenBundle(body)?.idToken

        /** Parses an authorization-code / refresh grant body → token bundle. */
        fun parseTokenBundle(body: String): TokenBundle? =
            runCatching {
                val obj = Json.parseToJsonElement(body).jsonObject
                val id = obj["id_token"]?.jsonPrimitive?.content
                if (id != null && looksLikeJwt(id)) {
                    TokenBundle(
                        idToken = id,
                        refreshToken = obj["refresh_token"]?.jsonPrimitive?.content,
                    )
                } else {
                    null
                }
            }.getOrNull()

        /** POSTs a token grant. Suspend-free; callers own the dispatcher. */
        private fun tokenBundle(
            code: String? = null,
            verifier: String? = null,
            refreshToken: String? = null,
        ): TokenBundle? {
            val grant =
                when {
                    code != null ->
                        "grant_type=authorization_code&client_id=$OAUTH_CLIENT_ID" +
                            "&code=${URLEncoder.encode(code, "UTF-8")}" +
                            "&code_verifier=${URLEncoder.encode(verifier ?: "", "UTF-8")}" +
                            "&redirect_uri=${URLEncoder.encode(OAUTH_REDIRECT_URI, "UTF-8")}"
                    else ->
                        "grant_type=refresh_token&client_id=$OAUTH_CLIENT_ID&refresh_token=" +
                            URLEncoder.encode(refreshToken ?: "", "UTF-8")
                }
            return httpPostToken(grant)?.let(::parseTokenBundle)
        }

        /** POSTs the grant body to the token endpoint. */
        internal fun httpPostToken(body: String): String? {
            val conn = URL(OAUTH_TOKEN_URL).openConnection() as HttpURLConnection
            return try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (X11; Linux x86_64; rv:156.0) Gecko/20100101 Firefox/156.0",
                )
                conn.outputStream.use { out ->
                    out.write(body.toByteArray())
                }
                if (conn.responseCode !in 200..299) return null
                conn.inputStream
                    .bufferedReader()
                    .use { it.readText() }
            } catch (_: Exception) {
                null
            } finally {
                conn.disconnect()
            }
        }
    }
}
