package com.rjbiermann.giffyviewer.core.database

import com.rjbiermann.giffyviewer.core.model.Gif

fun GifEntity.toModel(): Gif =
    Gif(
        id = id,
        userName = userName,
        tags = tags,
        likes = likes,
        views = views,
        durationSeconds = durationSeconds,
        hasAudio = hasAudio,
        width = width,
        height = height,
        createDateEpoch = createDateEpoch,
        published = published,
        avgColor = avgColor,
        sdUrl = sdUrl,
        hdUrl = hdUrl,
        posterUrl = posterUrl,
        niches = niches,
    )

fun Gif.toEntity(now: Long): GifEntity =
    GifEntity(
        id = id,
        userName = userName,
        tags = tags,
        niches = niches,
        likes = likes,
        views = views,
        durationSeconds = durationSeconds,
        hasAudio = hasAudio,
        width = width,
        height = height,
        createDateEpoch = createDateEpoch,
        published = published,
        avgColor = avgColor,
        sdUrl = sdUrl,
        hdUrl = hdUrl,
        posterUrl = posterUrl,
        fetchedAt = now,
    )
