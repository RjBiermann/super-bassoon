@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.rjbiermann.giffyviewer.core.player

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl

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
        // Neighbor preloading (PLAN §9): prepare-only target — manifest + init
        // segments are cached, media body loads on play (no data burn).
        val builder =
            DefaultPreloadManager
                .Builder(
                    context,
                    TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> {
                        DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_SOURCE_PREPARED
                    },
                ).setMediaSourceFactory(DefaultMediaSourceFactory(cached))
                .setCache(cache)
        val player = builder.buildExoPlayer()
        return GiffyPlayer(player, builder.build())
    }
}
