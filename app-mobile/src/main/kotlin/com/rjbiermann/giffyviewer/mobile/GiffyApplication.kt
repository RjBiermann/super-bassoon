package com.rjbiermann.giffyviewer.mobile

import android.app.Application
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.network.rate.BreakerReconnectReset
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PLAN §2 user-token refresh: proactive timer (~45 min — ID tokens live 1h)
 * alongside the on-401 fallback in the auth interceptor. Application-owned
 * structured scope (never GlobalScope): lives as long as the process.
 */
@HiltAndroidApp
class GiffyApplication : Application() {
    @Inject lateinit var tokenStore: TokenStore

    /** Issue #63: keeps the shared circuit breaker honest across reconnects. */
    @Inject lateinit var breakerReconnectReset: BreakerReconnectReset

    @Inject lateinit var db: GiffyDatabase

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Issue #63: offline -> online resets the breaker so the reconnect retry
        // gets a real attempt instead of the synthetic "503 circuit open".
        appScope.launch { breakerReconnectReset.observe() }
        // TTL sweep (AGENTS-DATABASE): feed pages/gif metadata are TTL-checked on
        // read but were never deleted, so both tables grew for the process life.
        appScope.launch {
            val cutoff = System.currentTimeMillis() - FeedSource.TTL_GIF_METADATA
            db.feedPageDao().evictStale(cutoff)
            db.gifDao().evictStale(cutoff)
        }
        appScope.launch {
            while (isActive) {
                delay(REFRESH_EVERY_MS)
                // No-op when logged out or already unrefreshable (no refresh token).
                tokenStore.refreshBlocking()
            }
        }
    }

    private companion object {
        const val REFRESH_EVERY_MS = 45L * 60 * 1_000
    }
}
