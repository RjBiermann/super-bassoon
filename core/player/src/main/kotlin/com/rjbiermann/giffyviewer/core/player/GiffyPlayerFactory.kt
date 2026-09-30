@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.rjbiermann.giffyviewer.core.player

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Builds [GiffyPlayer]s sharing one [SimpleCache] (one instance per process —
 * Media3 requirement). Cache size is the app module's concern (mobile default
 * 256 MB, PLAN §5).
 */
class GiffyPlayerFactory(
    private val cache: SimpleCache,
) {
    fun create(context: Context): GiffyPlayer {
        val upstream: DataSource.Factory = DefaultDataSource.Factory(context)
        val cached =
            CacheDataSource
                .Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstream)
                // stream while writing to cache; never fail playback because of cache errors
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val player =
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(cached))
                .setSeekBackIncrementMs(5_000)
                .setSeekForwardIncrementMs(5_000)
                .build()
        return GiffyPlayer(player)
    }
}
