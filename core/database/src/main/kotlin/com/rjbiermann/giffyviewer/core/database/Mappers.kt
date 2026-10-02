package com.rjbiermann.giffyviewer.core.database

import com.rjbiermann.giffyviewer.core.model.Gif

fun GifEntity.toModel(): Gif =
    Gif(
        id = id,
        userName = userName,
        description = description,
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
        verified = verified,
    )

fun Gif.toEntity(now: Long): GifEntity =
    GifEntity(
        id = id,
        userName = userName,
        description = description,
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
        verified = verified,
        fetchedAt = now,
    )
