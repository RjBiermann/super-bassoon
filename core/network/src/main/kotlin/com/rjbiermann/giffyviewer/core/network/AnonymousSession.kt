package com.rjbiermann.giffyviewer.core.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Anonymous browsing session (PLAN §2): fetches one temp token lazily and reuses it.
 * Racing callers WAIT on the mutex for the in-flight fetch instead of going out
 * unauthenticated (unauthenticated feed calls 401 and Paging never auto-retries them).
 *
 * #64: [api] must be the token-only client ([NetworkComponents.tokenApi]), NEVER the
 * shared one. The auth interceptor resolves the token with `runBlocking` while its own
 * request holds a `maxRequestsPerHost` slot, so a token fetch queued on that same
 * client can never be dispatched — 5 parked calls deadlock the process permanently.
 * The token client's own dispatcher breaks that cycle; [acquireTimeoutMs] bounds the
 * wait so even a stuck fetch fails open (no Bearer) instead of hanging the screen.
 */
class AnonymousSession(
    private val api: GifsApi,
    private val acquireTimeoutMs: Long = 10_000,
) {
    private val mutex = Mutex()

    @Volatile
    private var token: String? = null

    @Volatile
    private var fetchedAt = 0L

    suspend fun token(): String? =
        withTimeoutOrNull(acquireTimeoutMs) {
            fresh()?.let { return@withTimeoutOrNull it }
            // racing callers wait for the in-flight fetch; token endpoint is auth-exempt
            mutex.withLock {
                fresh()?.let { return@withLock it }
                token =
                    try {
                        api.temporaryToken().token
                    } catch (_: Exception) {
                        null
                    }
                if (token != null) fetchedAt = System.currentTimeMillis()
                token
            }
        }

    fun invalidate() {
        token = null
        fetchedAt = 0
    }

    private fun fresh(): String? = token?.takeIf { System.currentTimeMillis() - fetchedAt < REFRESH_MS }

    companion object {
        // Temp tokens live ~24h; refresh proactively at 1h.
        private const val REFRESH_MS = 60 * 60_000L
    }
}
