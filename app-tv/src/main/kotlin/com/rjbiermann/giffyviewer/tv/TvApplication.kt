package com.rjbiermann.giffyviewer.tv

import android.app.Application
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** TV shell: same proactive token-refresh timer as mobile (PLAN §2). */
@HiltAndroidApp
class TvApplication : Application() {
    @Inject lateinit var tokenStore: TokenStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            while (isActive) {
                delay(REFRESH_EVERY_MS)
                tokenStore.refreshBlocking()
            }
        }
    }

    private companion object {
        const val REFRESH_EVERY_MS = 45L * 60 * 1_000
    }
}
