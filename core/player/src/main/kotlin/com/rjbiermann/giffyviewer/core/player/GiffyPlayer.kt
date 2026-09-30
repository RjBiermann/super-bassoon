@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.rjbiermann.giffyviewer.core.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.model.streamUrl

/**
 * ExoPlayer wrapper (PLAN §5). [SimpleCache] is keyed by gif ID — passed as
 * MediaItem.customCacheKey so URL rotation never matters. Streaming + caching:
 * CacheDataSource (non-blocking flag) plays while writing to cache.
 */
class GiffyPlayer internal constructor(
    private val player: ExoPlayer,
) : ExoPlayer by player {
    fun playGif(
        gif: Gif,
        dataSaver: Boolean,
        resumeMs: Long = 0L,
    ) {
        val url = gif.streamUrl(dataSaver) ?: return
        val item =
            MediaItem
                .Builder()
                .setUri(url)
                .setCustomCacheKey(gif.id) // cache key = gif ID, never the URL (PLAN §5)
                .build()
        setMediaItem(item, resumeMs)
        prepare()
        playWhenReady = true
    }

    val currentPositionMs: Long
        get() = player.currentPosition.coerceAtLeast(0)

    val durationMs: Long
        get() = player.duration.takeIf { it != C.TIME_UNSET } ?: 0
}
