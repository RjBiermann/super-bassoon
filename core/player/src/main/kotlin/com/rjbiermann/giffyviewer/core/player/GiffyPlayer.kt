@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.rjbiermann.giffyviewer.core.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.model.VideoQuality
import com.rjbiermann.giffyviewer.core.model.qualityStreamUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ExoPlayer wrapper (PLAN §5). [SimpleCache] is keyed by "gif ID + rendition
 * tier" ([renditionCacheKey], issue #56) so an SD-cached stream can never
 * serve an HD request. Streaming + caching:
 * CacheDataSource (non-blocking flag) plays while writing to cache.
 */
class GiffyPlayer internal constructor(
    private val player: ExoPlayer,
    private val preloadManager: DefaultPreloadManager,
) : ExoPlayer by player {
    /** Id of the gif this player is (about to be) playing — flips BEFORE the
     *  media swaps so surfaces attach to the right content (no stale flash). */
    private val mutableCurrentGifId = MutableStateFlow<String?>(null)
    val currentGifId: StateFlow<String?> = mutableCurrentGifId.asStateFlow()

    /**
     * Preferred quality (§5, issue #4) picks the stream URL via the shared
     * [Gif.qualityStreamUrl] (dataSaver keeps its first rule; the probe-closed
     * sd/hd vocabulary is a stream-URL pick, no track machinery). The custom
     * cache key is rendition-namespaced ([renditionCacheKey], issue #56) so a
     * pref flip re-picks both URL and tier.
     */
    fun playGif(
        gif: Gif,
        dataSaver: Boolean,
        resumeMs: Long = 0L,
        videoQuality: String = VideoQuality.AUTO,
    ) {
        val url = gif.qualityStreamUrl(dataSaver, videoQuality) ?: return
        mutableCurrentGifId.value = gif.id
        val item =
            MediaItem
                .Builder()
                .setUri(url)
                .setCustomCacheKey(gif.renditionCacheKey(url))
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
     * player (same factory) — no double prepare. Preload honors the same
     * quality pick as playback so the cache holds the bits the player plays.
     */
    fun preloadNeighbors(
        gifs: List<Gif>,
        dataSaver: Boolean,
        videoQuality: String = VideoQuality.AUTO,
    ) {
        preloaded.forEach { preloadManager.remove(it) }
        preloaded =
            gifs.mapNotNull { gif ->
                gif.qualityStreamUrl(dataSaver, videoQuality)?.let { url ->
                    MediaItem
                        .Builder()
                        .setUri(url)
                        .setCustomCacheKey(gif.renditionCacheKey(url))
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

/**
 * Rendition-namespaced SimpleCache key (issue #56): the pick that resolves to
 * the SD stream caches under `<id>:sd`, anything else `<id>:hd`, so a cached
 * SD rendition (TV focus preview, data-saver watch) can never serve an HD
 * request. Same-tier URL rotation still doesn't matter — the key follows the
 * picked tier, not the URL bytes. Pre-#56 un-namespaced `<id>` entries are
 * dead bytes; LRU evicts them (no migration).
 */
internal fun Gif.renditionCacheKey(pickedUrl: String): String =
    if (pickedUrl == sdUrl) "$id:sd" else "$id:hd"

