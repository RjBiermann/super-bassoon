package com.rjbiermann.giffyviewer.core.network

import kotlinx.coroutines.sync.Mutex

/**
 * Anonymous browsing session (PLAN §2): fetches one temp token lazily and reuses it.
 * Reentrancy-safe: the /v2/auth/temporary call must go out UNauthenticated — the auth
 * interceptor skips auth for that endpoint, so racing callers WAIT on the mutex for the
 * in-flight fetch instead of going unauthenticated (unauthenticated feed calls 401 and
 * Paging never auto-retries them).
 */
class AnonymousSession(
    private val api: upstreamApi,
) {
    private val mutex = Mutex()

    @Volatile
    private var token: String? = null

    @Volatile
    private var fetchedAt = 0L

    suspend fun token(): String? {
        token?.takeIf { System.currentTimeMillis() - fetchedAt < REFRESH_MS }?.let { return it }
        mutex.lock() // racing callers wait for the in-flight fetch; token endpoint is auth-exempt
        try {
            token?.takeIf { System.currentTimeMillis() - fetchedAt < REFRESH_MS }?.let { return it }
            token =
                try {
                    api.temporaryToken().token
                } catch (_: Exception) {
                    null
                }
            if (token != null) fetchedAt = System.currentTimeMillis()
            return token
        } finally {
            mutex.unlock()
        }
    }

    fun invalidate() {
        token = null
        fetchedAt = 0
    }

    companion object {
        // Temp tokens live ~24h; refresh proactively at 1h.
        private const val REFRESH_MS = 60 * 60_000L
    }
}
