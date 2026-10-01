package com.rjbiermann.giffyviewer.tv

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
import com.rjbiermann.giffyviewer.core.network.NetworkComponents
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.buildNetwork
import com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * Same wiring as the mobile AppModule (mirrors it on purpose — the modules stay
 * device-shaped). TV media cache is 1 GB (PLAN §5).
 */
@Module
@InstallIn(SingletonComponent::class)
object TvAppModule {
    private val Context.giffySettings by preferencesDataStore(name = "giffy_settings")

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
                    // Refresh failed → clear the stale user token (PLAN §2) so
                    // browsing falls back to the anonymous session (see mobile).
                    if (!tokenStore.refreshBlocking()) {
                        tokenStore.clear()
                        session?.invalidate()
                    } else {
                        session?.invalidate()
                    }
                },
                enableLogging = false,
            )
        session = AnonymousSession(nc.api)
        return nc
    }

    @Provides
    fun api(network: NetworkComponents): GifsApi = network.api

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
            ).build()

    /** Images share the API's OkHttp instance → same rate limiter covers media. */
    @Provides
    @Singleton
    fun imageLoader(
        @ApplicationContext context: Context,
        network: NetworkComponents,
    ): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(network.client)) }
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

    // TV default 1 GB (PLAN §5); 512MB/1GB/2GB options arrive with Phase 7
    private const val MEDIA_CACHE_BYTES = 1024L * 1024 * 1024
}
