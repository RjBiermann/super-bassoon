package com.rjbiermann.giffyviewer.core.model

/** Niche membership on a gif: id for routing, name for the chip label
 *  (payload carries both; Room stores ids + a name map — DB v9). */
data class NicheRef(
    val id: String,
    val name: String,
)

/** A single upstream gif — everything the UI needs, nothing it doesn't. */
data class Gif(
    val id: String,
    val userName: String,
    /** Free-text caption from the API (no title field exists); often null. */
    val description: String?,
    val tags: List<String>,
    val likes: Long,
    val views: Long,
    val durationSeconds: Double,
    val hasAudio: Boolean,
    val width: Int,
    val height: Int,
    val createDateEpoch: Long,
    val published: Boolean,
    val avgColor: String,
    val sdUrl: String?,
    val hdUrl: String?,
    val posterUrl: String?,
    val niches: List<NicheRef>,
    /** Creator's verified badge (gif payload top-level `verified`, live 2026-10). */
    val verified: Boolean = false,
)

/** Read-oriented resolution for the data-saver toggle: SD vs HD stream. */
fun Gif.streamUrl(dataSaver: Boolean): String? =
    when {
        dataSaver -> sdUrl ?: hdUrl
        else -> hdUrl ?: sdUrl
    }

/**
 * Orientation preference check (AGENTS-APP.md 2026-10): "any" passes all;
 * "vertical"/"horizontal" use the side comparison. Unknown dimensions (0x0)
 * pass through — same fallback as the TV card-width rule.
 */
fun Gif.matchesOrientation(pref: String): Boolean =
    when (pref) {
        "vertical" -> height >= width || width <= 0 || height <= 0
        "horizontal" -> width >= height || width <= 0 || height <= 0
        else -> true
    }

/** §8 resolution chip: "hd" = an HD stream must exist; "" / "sd" pass all
 *  (SD streams exist on every gif). */
fun Gif.resolutionMatches(chip: String): Boolean =
    when (chip) {
        "hd" -> hdUrl != null
        else -> true
    }
