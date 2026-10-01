package com.rjbiermann.giffyviewer.mobile

import android.app.Application
import com.rjbiermann.giffyviewer.core.auth.TokenStore
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
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
