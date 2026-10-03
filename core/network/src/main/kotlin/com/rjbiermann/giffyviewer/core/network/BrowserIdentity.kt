package com.rjbiermann.giffyviewer.core.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.security.SecureRandom

/**
 * Full browser impersonation (user-approved policy, 2026-10-03): the API is
 * browser-made, so every request carries the exact header set the site's own
 * SPA sends — UA, client hints, Sec-Fetch-*, Accept, Accept-Language,
 * Referer/Origin, plus the SPA's client-generated `x-session-id`.
 *
 * Live-probe findings 2026-10-03 (see AGENTS-NETWORK.md "Browser impersonation"):
 * - No single header is hard-required (default-UA requests still 200), but the
 *   anonymous token is **UA-bound**: the JWT embeds a `valid_agent` claim set
 *   at issuance, and a later request with a different UA → `401 WrongSender`.
 *   The token fetch and every subsequent request must therefore carry the SAME
 *   UA — one identity source ([BrowserIdentity]) guarantees that.
 * - The site's own desktop SPA observed sending: `sec-ch-ua` (v154), `referer`
 *   = site root, `accept: application/json, text/plain` wildcard, `x-session-id`
 *   (a ~19-digit client-generated number). Home feed page size: count=50.
 * - Do NOT set `Accept-Encoding` here: OkHttp's bridge adds `gzip` by default
 *   and decompresses transparently. Claiming `br` would hand us undecoded
 *   bodies (brotli needs an extra dependency — not allowed).
 * - UA + headers are not credentials (no secrets in code): they are the public
 *   client identity any browser session on the site sends.
 */
object BrowserIdentity {
    /**
     * Chrome 154 on Android 16 (Pixel 9) — current, Chrome-Android-shaped. The
     * app IS an Android client, so the Chrome-Android identity is the truthful
     * one (desktop UA would be a mismatch for a handheld device).
     */
    const val USER_AGENT: String =
        "Mozilla/5.0 (Linux; Android 16; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Mobile Safari/537.36"

    /** Client hints consistent with [USER_AGENT] (Chrome-Android brand list format). */
    const val SEC_CH_UA: String = "\"Chromium\";v=\"154\", \"Google Chrome\";v=\"154\", \"Not-A.Brand\";v=\"99\""
    const val SEC_CH_UA_MOBILE: String = "?1"
    const val SEC_CH_UA_PLATFORM: String = "\"Android\""

    /** The SPA's XHR Accept (captured live) + Indian English preference. */
    const val ACCEPT: String = "application/json, text/plain, */*"
    const val ACCEPT_LANGUAGE: String = "en-IN,en-GB;q=0.9,en-US;q=0.8,en;q=0.7"

    /** The SPA calls the API host from the site origin: a same-site CORS fetch. */
    const val SEC_FETCH_SITE: String = "same-site"
    const val SEC_FETCH_MODE: String = "cors"
    const val SEC_FETCH_DEST: String = "empty"

    /**
     * One session id per network stack (the SPA generates one per web session).
     * ~19 digits, client-side, no secret material.
     */
    fun newSessionId(): String {
        val value = 1_000_000_000_000_000_000L + (SecureRandom().nextLong() and Long.MAX_VALUE) % 9_000_000_000_000_000_000L
        return value.toString()
    }

    /** Applies the full browser header set. [site] is the site root ([Hosts.site]). */
    fun apply(
        request: Request,
        sessionId: String,
        site: String,
    ): Request =
        request
            .newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Sec-Ch-Ua", SEC_CH_UA)
            .header("Sec-Ch-Ua-Mobile", SEC_CH_UA_MOBILE)
            .header("Sec-Ch-Ua-Platform", SEC_CH_UA_PLATFORM)
            .header("Accept", ACCEPT)
            .header("Accept-Language", ACCEPT_LANGUAGE)
            .header("Origin", site)
            .header("Referer", "$site/")
            .header("Sec-Fetch-Site", SEC_FETCH_SITE)
            .header("Sec-Fetch-Mode", SEC_FETCH_MODE)
            .header("Sec-Fetch-Dest", SEC_FETCH_DEST)
            .header("X-Session-Id", sessionId)
            .build()
}

/** Adds the browser identity to every request; first interceptor in the chain. */
class BrowserIdentityInterceptor(
    private val sessionId: String = BrowserIdentity.newSessionId(),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(BrowserIdentity.apply(chain.request(), sessionId, BASE_URL))
}
