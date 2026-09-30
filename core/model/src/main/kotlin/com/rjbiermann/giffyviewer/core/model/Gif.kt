package com.rjbiermann.giffyviewer.core.model

/** A single upstream gif — everything the UI needs, nothing it doesn't. */
data class Gif(
    val id: String,
    val userName: String,
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
    val niches: List<String>,
)

/** Read-oriented resolution for the data-saver toggle: SD vs HD stream. */
fun Gif.streamUrl(dataSaver: Boolean): String? =
    when {
        dataSaver -> sdUrl ?: hdUrl
        else -> hdUrl ?: sdUrl
    }
