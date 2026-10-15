package com.rjbiermann.giffyviewer.core.player

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #20 (SDR-pinned TV playback): the codec-level filter predicate.
 *  Per-family HDR markers ONLY — profile values collide across families
 *  (DolbyVisionProfileDvavPer == HEVCProfileMain == 1), so family scoping is
 *  part of the rule, not a nicety. */
class SdrCodecFilterTest {
    @Test
    fun `dolby vision path drops by MIME`() {
        assertTrue(isHdrOnlyDecoder(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION, emptyList()))
        assertTrue(
            isHdrOnlyDecoder(
                MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION,
                listOf(CodecProfileLevel.HEVCProfileMain),
            ),
        )
    }

    @Test
    fun `hdr-only decoders drop per family`() {
        assertTrue(isHdrOnlyDecoder("video/hevc", listOf(CodecProfileLevel.HEVCProfileMain10HDR10)))
        assertTrue(
            isHdrOnlyDecoder(
                "video/av01",
                listOf(CodecProfileLevel.AV1ProfileMain10HDR10, CodecProfileLevel.AV1ProfileMain10HDR10Plus),
            ),
        )
        assertTrue(
            isHdrOnlyDecoder(
                "video/x-vnd.on2.vp9",
                listOf(
                    CodecProfileLevel.VP9Profile2HDR,
                    CodecProfileLevel.VP9Profile3HDR10Plus,
                ),
            ),
        )
    }

    @Test
    fun `sdr-capable decoders survive - acceptance 2`() {
        // Plain SDR profile survives — the DV-collision regression case (SdrCodecFilterTest).
        assertFalse(isHdrOnlyDecoder("video/hevc", listOf(CodecProfileLevel.HEVCProfileMain)))
        // A 10-bit SDR decoder (Main10 is not HDR by itself) survives.
        assertFalse(isHdrOnlyDecoder("video/hevc", listOf(CodecProfileLevel.HEVCProfileMain10)))
        // SDR + HDR-capable box decoder survives (SDR keeps playing).
        assertFalse(
            isHdrOnlyDecoder(
                "video/hevc",
                listOf(CodecProfileLevel.HEVCProfileMain, CodecProfileLevel.HEVCProfileMain10HDR10),
            ),
        )
        // No profile info: cannot tell → keep (conservative, SDR continues).
        assertFalse(isHdrOnlyDecoder("video/hevc", emptyList()))
        // Unknown family: no markers → keep (conservative).
        assertFalse(isHdrOnlyDecoder("video/xyz", listOf(CodecProfileLevel.HEVCProfileMain)))
    }
}
