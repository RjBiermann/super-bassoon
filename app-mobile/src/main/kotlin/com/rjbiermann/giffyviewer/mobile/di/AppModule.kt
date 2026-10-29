package com.rjbiermann.giffyviewer.mobile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.room.Room
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.rjbiermann.giffyviewer.core.auth.TokenStore
import com.rjbiermann.giffyviewer.core.database.GiffyDatabase
import com.rjbiermann.giffyviewer.core.network.AnonymousSession
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.NetworkComponents
import com.rjbiermann.giffyviewer.core.network.buildNetwork
import com.rjbiermann.giffyviewer.core.network.rate.BreakerReconnectReset
import com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory
import com.rjbiermann.giffyviewer.core.ui.NetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private val Context.giffySettings by androidx.datastore.preferences.preferencesDataStore(name = "giffy_settings")

    @Provides
    @Singleton
    fun tokenStore(
        @ApplicationContext context: Context,
    ): TokenStore = TokenStore(context)

    @Provides
    @Singleton
    fun network(tokenStore: TokenStore): NetworkComponents {
        var session: AnonymousSession? = null
        // Signed-in token wins; anonymous temp token is the fallback. On 401:
        // try a silent refresh-token grant first; only if that fails (logged out
        // or refresh revoked) fall back to the anonymous session.
        val nc =
            buildNetwork(
                authToken = { tokenStore.tokenOrNull() ?: session?.token() },
                onUnauthorized = {
                    // Silent refresh first; if the grant is dead (revoked/expired
                    // family), CLEAR the stale user token (PLAN §2: refresh fails
                    // → clear state) so browsing falls back to the anonymous
                    // session instead of 401-looping forever.
                    if (!tokenStore.refreshBlocking()) {
                        tokenStore.clear()
                        session?.invalidate()
                    } else {
                        session?.invalidate()
                    }
                },
            )
        session = AnonymousSession(nc.tokenApi)
        return nc
    }

    @Provides
    fun api(network: NetworkComponents): GifsApi = network.api

    /** Issue #63: an offline -> online flip resets the shared breaker (its 5-minute
     *  cooldown is time-based and nothing else generates traffic while parked, so
     *  the reconnect retries used to burn their budget against an open circuit).
     *  Reads the SAME #33 NetworkMonitor singleton the offline banner uses — no
     *  second ConnectivityManager callback, and core:network stays Android-free. */
    @Provides
    @Singleton
    fun breakerReconnectReset(
        network: NetworkComponents,
        networkMonitor: NetworkMonitor,
    ): BreakerReconnectReset = BreakerReconnectReset(network.breaker, networkMonitor.isOnline)

    /** Issue #33: OS connectivity behind the offline banner (TokenStore shape). */
    @Provides
    @Singleton
    fun networkMonitor(
        @ApplicationContext context: Context,
    ): NetworkMonitor = NetworkMonitor(context)

    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): GiffyDatabase =
        Room
            .databaseBuilder(
                context,
                GiffyDatabase::class.java,
                GiffyDatabase.NAME,
            ).addMigrations(
                GiffyDatabase.MIGRATION_1_2,
                GiffyDatabase.MIGRATION_2_3,
                GiffyDatabase.MIGRATION_3_4,
                GiffyDatabase.MIGRATION_4_5,
                GiffyDatabase.MIGRATION_5_6,
                GiffyDatabase.MIGRATION_6_7,
                GiffyDatabase.MIGRATION_7_8,
                GiffyDatabase.MIGRATION_8_9,
                GiffyDatabase.MIGRATION_9_10,
                GiffyDatabase.MIGRATION_10_11,
            ).build()

    /**
     * Images get a derivative of the API's OkHttp client: SAME rate limiter
     * (the ≤15/5s invariant covers media too), same identity/auth headers,
     * but NO RetryInterceptor — a burst of failing poster loads must not feed
     * the API circuit breaker (issue #58: one breaker opened every feed).
     */
    @Provides
    @Singleton
    fun imageLoader(
        @ApplicationContext context: Context,
        network: NetworkComponents,
    ): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(network.imageClient)) }
            .build()

    @Provides
    @Singleton
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun mediaCache(
        @ApplicationContext context: Context,
    ): SimpleCache =
        SimpleCache(
            File(context.cacheDir, "giffy_media"),
            LeastRecentlyUsedCacheEvictor(MEDIA_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )

    @Provides
    @Singleton
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun playerFactory(cache: SimpleCache): GiffyPlayerFactory = GiffyPlayerFactory(cache)

    @Provides
    @Singleton
    fun settingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.giffySettings

    // Fixed constants, PLAN §5: mobile 512MB / TV 1GB (not DataStore-configurable)
    private const val MEDIA_CACHE_BYTES = 512L * 1024 * 1024
}
