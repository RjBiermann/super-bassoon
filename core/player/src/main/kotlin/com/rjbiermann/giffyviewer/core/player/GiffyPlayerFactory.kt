@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.rjbiermann.giffyviewer.core.player

import android.content.Context
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl

/**
 * Builds [GiffyPlayer]s sharing one [SimpleCache] (one instance per process —
 * Media3 requirement). Cache size is the app module's concern (mobile default
 * 256 MB, PLAN §5).
 *
 * [sdrPlayback] (issue #20, TV only): pin playback to SDR decoders. media3
 * 1.11.1 has NO parameters-level HDR knob (verified against the 1.11.1 AAR:
 * `TrackSelectionParameters.Builder` carries size/framerate/bitrate/MIME
 * preference sets only; no blocked-MIME or HDR exclusion), so the
 * codec-level route applies: a `MediaCodecSelector` wrapper drops decoder
 * candidates that cannot serve SDR, wired via
 * `DefaultPreloadManager.Builder.setRenderersFactory` (setter verified on
 * 1.11.1). SDR-only devices: no behavior change (decoders pass the filter).
 *
 * What the filter can and cannot do, stated honestly: Dolby Vision decoders
 * are excluded by their own MIME type; HEVC/AV1/VP9 HDR10(HL/Plus) profiles
 * are excluded ONLY when a decoder claims no SDR profile at all — a 10-bit
 * SDR decoder (HEVCProfileMain10, AV1ProfileMain10) is kept, so HDR10
 * variants MAY still be selected by a decoder that lists both SDR and HDR
 * profiles. That residual window needs a live HDR-variant probe (design
 * doc verification list) before it can be closed; the factory flag is the
 * honest plumbing.
 */
class GiffyPlayerFactory(
    private val cache: SimpleCache,
    /** TV-only construction sites pass true (mobile default false). */
    private val sdrPlayback: Boolean = false,
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
        if (sdrPlayback) {
            // Issue #20 (API verified against media3 1.11.1): selector wrapper
            // filters HDR-only codec candidates; DEFAULT is untouched otherwise.
            val renderersFactory = DefaultRenderersFactory(context).setMediaCodecSelector(SdrDecoderSelector)
            builder.setRenderersFactory(renderersFactory)
        }
        val player = builder.buildExoPlayer()
        return GiffyPlayer(player, builder.build())
    }
}

/** Issue #20: true when this decoder candidate CANNOT serve an SDR rendition.
 *  Dolby-Vision decoders are HDR-only by definition. For other families the
 *  declared profiles are matched against THAT family's own HDR markers inly —
 *  IMPORTANT: profile values COLLIDE across families (DolbyVisionProfileDvavPer
 *  == HEVCProfileMain == 1, DvavPen == HEVCProfileMain10 == 2 in the API), so a
 *  single global set would misclassify every SDR HEVC decoder — the family
 *  scoping is not optional. Pure — unit tested in SdrCodecFilterTest. */
internal fun isHdrOnlyDecoder(
    codecMimeType: String,
    profiles: List<Int>,
): Boolean {
    if (codecMimeType == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION) return true
    val hdrMarkers = hdrProfilesFor(codecMimeType)
    if (hdrMarkers.isEmpty() || profiles.isEmpty()) return false
    return profiles.all { it in hdrMarkers }
}

/** HDR-declared profile markers per MIME family (API 35 android.jar). HEVC HLG
 *  has NO profile constant — HDR10/HDR10Plus are the closest declared markers
 *  (residual window: SDR-capable HLG decoders stay available). */
private fun hdrProfilesFor(codecMimeType: String): Set<Int> =
    when (codecMimeType) {
        MediaFormat.MIMETYPE_VIDEO_HEVC ->
            setOf(
                CodecProfileLevel.HEVCProfileMain10HDR10,
                CodecProfileLevel.HEVCProfileMain10HDR10Plus,
            )
        MediaFormat.MIMETYPE_VIDEO_AV1 ->
            setOf(
                CodecProfileLevel.AV1ProfileMain10HDR10,
                CodecProfileLevel.AV1ProfileMain10HDR10Plus,
            )
        MediaFormat.MIMETYPE_VIDEO_VP9 ->
            setOf(
                CodecProfileLevel.VP9Profile2HDR,
                CodecProfileLevel.VP9Profile2HDR10Plus,
                CodecProfileLevel.VP9Profile3HDR,
                CodecProfileLevel.VP9Profile3HDR10Plus,
            )
        else -> emptySet()
    }

/** Issue #20 selector: identical result order to DEFAULT, minus HDR-only
 *  decoder candidates (SDR playback keeps every SDR-capable codec). */
private val SdrDecoderSelector =
    MediaCodecSelector { mimeType, requiresCodecSupport, requiresTunnelingDecoder ->
        MediaCodecSelector
            .DEFAULT
            .getDecoderInfos(mimeType, requiresCodecSupport, requiresTunnelingDecoder)
            .filter { info ->
                !isHdrOnlyDecoder(
                    info.codecMimeType,
                    info.getProfileLevels().map { pl -> pl.profile },
                )
            }
    }
