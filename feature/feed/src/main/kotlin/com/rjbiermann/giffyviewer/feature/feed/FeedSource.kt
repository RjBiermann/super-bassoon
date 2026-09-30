package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.model.Gif

/**
 * Feed identity = cache key base (PLAN §7). Sort/range changes produce distinct
 * bases ("trend:v2:pop:sort=top_week"), client-side ops don't.
 */
sealed interface FeedSource {
    /** Base cache key for page keys "base:p<n>". */
    val keyBase: String

    /** TTL in ms: REFRESH re-hits the API only when stale (PLAN §5). */
    val ttlMs: Long

    data object Trending : FeedSource {
        override val keyBase = "trend:v2:pop"
        override val ttlMs = TTL_TRENDING
    }

    data object Discover : FeedSource {
        override val keyBase = "trend:v2:discover"
        override val ttlMs = TTL_TRENDING
    }

    data class Search(
        val query: String,
    ) : FeedSource {
        override val keyBase = "search:${query.lowercase().trim()}"
        override val ttlMs = TTL_SEARCH
    }

    /** Recency round-robin over favorited creators (PLAN §7). */
    data object Favorites : FeedSource {
        override val keyBase = "fav:v1"
        override val ttlMs = TTL_TRENDING
    }

    companion object {
        const val TTL_TRENDING = 10 * 60_000L
        const val TTL_SEARCH = 60 * 60_000L
    }
}

fun feedPageKey(
    source: FeedSource,
    page: Int,
): String = "${source.keyBase}:p$page"

/** Page number back out of a "base:p<n>" key. */
fun pageNumber(pageKey: String?): Int? = pageKey?.substringAfterLast(":p", "")?.toIntOrNull()

/** A feed's UI-facing identity for titles. */
fun FeedSource.title(): String =
    when (this) {
        is FeedSource.Trending -> "Trending"
        is FeedSource.Discover -> "Discover"
        is FeedSource.Search -> "Search: $query"
        is FeedSource.Favorites -> "Favorites"
    }

typealias GifItem = Gif
