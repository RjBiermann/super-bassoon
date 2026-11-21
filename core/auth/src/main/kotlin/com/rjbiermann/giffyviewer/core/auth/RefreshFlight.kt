package com.rjbiermann.giffyviewer.core.auth

import java.util.concurrent.locks.ReentrantLock

/**
 * Single-flight guard for the 401 refresh grant. OkHttp delivers 401s from many
 * worker threads at once (the API and image clients share the auth interceptor),
 * and each one used to start its own blocking grant against the token endpoint.
 *
 *  - serialize: one grant at a time, so a slow grant cannot breed N more;
 *  - dedupe: when the stored token already differs from the one that 401'd,
 *    another caller refreshed — reuse that result instead of granting again.
 */
internal class RefreshFlight {
    private val lock = ReentrantLock()

    /**
     * @param staleToken bearer that produced the 401 (null when unknown)
     * @param currentToken reads the token stored right now
     * @param grant performs the grant; true when a usable token exists afterwards
     */
    fun run(
        staleToken: String?,
        currentToken: () -> String?,
        grant: () -> Boolean,
    ): Boolean {
        lock.lock()
        try {
            if (alreadyRefreshed(staleToken, currentToken())) return true
            return grant()
        } finally {
            lock.unlock()
        }
    }
}

/** True when [staleToken] was already replaced by a newer [currentToken]. */
internal fun alreadyRefreshed(
    staleToken: String?,
    currentToken: String?,
): Boolean = staleToken != null && currentToken != null && currentToken != staleToken
