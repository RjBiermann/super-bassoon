@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.rjbiermann.giffyviewer.core.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.model.streamUrl

/**
 * ExoPlayer wrapper (PLAN §5). [SimpleCache] is keyed by gif ID — passed as
 * MediaItem.customCacheKey so URL rotation never matters. Streaming + caching:
 * CacheDataSource (non-blocking flag) plays while writing to cache.
 */
class GiffyPlayer internal constructor(
    private val player: ExoPlayer,
    private val preloadManager: DefaultPreloadManager,
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

    private var preloaded: List<MediaItem> = emptyList()

    /**
     * Adjacent-item prefetch (PLAN §9): prepare next/prev sources so the swipe
     * starts without the manifest-load stutter. Sources are reused by the
     * player (same factory) — no double prepare.
     */
    fun preloadNeighbors(
        gifs: List<Gif>,
        dataSaver: Boolean,
    ) {
        preloaded.forEach { preloadManager.remove(it) }
        preloaded =
            gifs.mapNotNull { gif ->
                gif.streamUrl(dataSaver)?.let { url ->
                    MediaItem
                        .Builder()
                        .setUri(url)
                        .setCustomCacheKey(gif.id)
                        .build()
                }
            }
        preloaded.forEach { preloadManager.add(it, 0) }
    }

    override fun release() {
        preloaded.forEach { preloadManager.remove(it) }
        preloadManager.release()
        player.release()
    }

    /** Play tap at end-of-stream restarts from the top (standard player UX). */
    fun playOrRestart() {
        if (!player.isPlaying && durationMs > 0 && player.currentPosition >= player.duration) {
            player.seekTo(0)
        }
        player.play()
    }
}
