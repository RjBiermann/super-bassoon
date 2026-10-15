package com.rjbiermann.giffyviewer.core.model

/**
 * §5 preferred video quality (issue #4) — THE one place for the menu value
 * list. LIVE MANIFEST PROBE CLOSED (2026-10): `urls.hd` and `urls.sd` are BOTH
 * plain direct faststart MP4s (video/mp4, no HLS master at the app's URLs;
 * guessed variant renditions 403) — the single-rendition branch of the design
 * applies. Discrete height caps would be an honest no-op (single video track),
 * so no TrackSelectionParameters machinery is built: the pref picks the
 * STREAM URL at playGif time and that's the whole mechanism.
 * - `auto` / `highest`: today's pick (`hdUrl ?: sdUrl`; highest ≈ auto on
 *   sd/hd-only vocabularies).
 * - `sd`: prefers `sdUrl` (falls back to hd when no sd exists) — independent
 *   of the data-saver state; dataSaver's own first rule still wins.
 */
object VideoQuality {
    const val AUTO = "auto"
    const val HIGHEST = "highest"
    const val SD = "sd"

    /** Settings row order (Auto is the default, first). */
    val VALUES: List<String> = listOf(AUTO, HIGHEST, SD)

    /** Chip labels lingo — shared by both shells (same row on the shared
     *  SettingsScreen). */
    fun label(value: String): String =
        when (value) {
            HIGHEST -> "Highest"
            SD -> "SD"
            else -> "Auto"
        }
}

/**
 * Preferred-quality stream pick (probe-closed single-rendition branch):
 * dataSaver keeps its FIRST rule (SD always, PLAN §5); otherwise the discrete
 * `sd` choice prefers the SD stream independent of dataSaver, and
 * auto/highest keep today's default (`hdUrl ?: sdUrl`).
 */
fun Gif.qualityStreamUrl(
    dataSaver: Boolean,
    quality: String,
): String? =
    when {
        dataSaver -> sdUrl ?: hdUrl
        quality == VideoQuality.SD -> sdUrl ?: hdUrl
        else -> hdUrl ?: sdUrl
    }
