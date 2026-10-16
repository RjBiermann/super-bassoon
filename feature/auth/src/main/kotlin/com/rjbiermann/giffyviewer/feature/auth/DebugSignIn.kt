package com.rjbiermann.giffyviewer.feature.auth

import android.util.Base64
import com.rjbiermann.giffyviewer.core.auth.TokenStore

/**
 * DEBUG-ONLY sign-in injection (issue #13 §8.x follow-flip device gate): the
 * shell Activities read the `giffy_debug_bundle` launch extra (base64 of a
 * token-bundle JSON) and call this. Release builds never reach the call site
 * (`if (BuildConfig.DEBUG)` guards in the Activities) — the channel cannot
 * exist outside debug installs, and the token itself never touches source.
 *
 * Parse is the same shape-tolerant JSON the (removed) paste-token path used:
 * full bundle = {id_token, refresh_token}; bare id_token also accepted.
 */
object DebugSignIn {
    fun apply(
        store: TokenStore,
        bundleB64: String?,
    ) {
        if (bundleB64.isNullOrBlank()) return
        val json = String(Base64.decode(bundleB64, Base64.DEFAULT), Charsets.UTF_8).trim()

        fun key(k: String): String? = Regex("\"?$k\"?\\s*[:=]\\s*\"?([A-Za-z0-9_.-]+)").find(json)?.groupValues?.get(1)
        val id = key("id_token")
        if (id == null) {
            store.save(json, null)
        } else {
            store.save(id, key("refresh_token"))
        }
    }
}
