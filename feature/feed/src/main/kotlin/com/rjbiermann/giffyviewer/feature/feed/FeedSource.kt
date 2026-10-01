package com.rjbiermann.giffyviewer.feature.feed

import com.rjbiermann.giffyviewer.core.model.Gif

/**
 * Feed identity = cache key base (PLAN §7). Sort/range changes produce distinct
 * bases ("trend:v2:pop:sort=top_week"), client-side ops don't.
 */
sealed interface FeedSource {
    /** Base cache key for page keys "base:p<n>". */
    val keyBase: String

    /** Sortless base (DataStore per-feed sort persistence keys on this). */
    val baseKey: String get() = keyBase

    /** TTL in ms: REFRESH re-hits the API only when stale (PLAN §5). */
    val ttlMs: Long

    data object Trending : FeedSource {
        override val keyBase = "trend:v2:pop"
        override val ttlMs = TTL_TRENDING
    }

    data class Search(
        val query: String,
        val sort: String = "",
    ) : FeedSource {
        override val keyBase = "search:${query.lowercase().trim()}" + if (sort.isEmpty()) "" else ":sort=$sort"
        override val baseKey = "search:${query.lowercase().trim()}"
        override val ttlMs = TTL_SEARCH
    }

    /** Niche feed — v2/niches/{id}/gifs (live-verified, anonymous OK). */
    data class Niche(
        val id: String,
        val name: String,
        val sort: String = "",
    ) : FeedSource {
        override val keyBase = "niche:$id" + if (sort.isEmpty()) "" else ":sort=$sort"
        override val baseKey = "niche:$id"
        override val ttlMs = TTL_SEARCH
    }

    /** Top This Week = trending/popular?order=top_week (live-verified). */
    data object TopThisWeek : FeedSource {
        override val keyBase = "topweek:v1"
        override val ttlMs = TTL_TRENDING
    }

    /** Logged-in personalized personalized server feed (PLAN §7); chip hidden when logged out. */
    data object ForYou : FeedSource {
        override val keyBase = "foryou:v1"
        override val ttlMs = TTL_TRENDING
    }

    /** Recency round-robin over favorited creators (PLAN §7). */
    data object Favorites : FeedSource {
        override val keyBase = "fav:v1"
        override val ttlMs = TTL_TRENDING
    }

    /** Single creator feed — v2/users/{username}/gifs (same call the Favorites
     *  round-robin already uses). Pinned creator tabs (PLAN §7 pin-to-tabs). */
    data class Creator(
        val username: String,
        val sort: String = "",
    ) : FeedSource {
        override val keyBase = "user:${username.lowercase().trim()}" + if (sort.isEmpty()) "" else ":sort=$sort"
        override val baseKey = "user:${username.lowercase().trim()}"
        override val ttlMs = TTL_SEARCH
    }

/**
     * Group feed (PLAN §7): a user-defined tag bundle fetched round-robin — page n
     * maps to tag[(n-1) % n_tags], same pattern as the Favorites creator rotation
     * (live-verified: v2/gifs/search's search_text matches tags). Cached group:<id>.
     */
    data class Group(
        val id: Long,
        val name: String,
        val tags: List<String>,
    ) : FeedSource {
        override val keyBase = "group:$id"
        override val ttlMs = TTL_SEARCH
    }

    /** Custom feed (PLAN §7 builder): named blend of creators/groups/tags,
     *  fetched round-robin like Group — page n maps to
     *  refs[(n-1) % n] with inner page (n-1)/n + 1. Sources types:
     *  "creator:<username>" · "group:<id>|<name>|<tags>" · "tag:<text>". */
    data class Custom(
        val id: Long,
        val name: String,
        val refs: List<String>,
    ) : FeedSource {
        override val keyBase = "custom:$id"
        override val ttlMs = TTL_SEARCH
    }

    /** "Surprise me" (§8): a random unwatched pool the repository refreshes
     *  per open — session-only, no Room rows, TTL 0 (always regenerate). */
    data object Surprise : FeedSource {
        override val keyBase = "surprise:v1"
        override val ttlMs = 0L
    }

    /** Liked gifs (PLAN §7, logged-in): network-LIVE — never cached (§5);
     *  pages have no cache keys and sorts are client-side only. */
    data object Liked : FeedSource {
        override val keyBase = "liked:v1"
        override val ttlMs = 0L
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
        is FeedSource.ForYou -> "For You"
        is FeedSource.Liked -> "Liked GIFs & Images"
        is FeedSource.TopThisWeek -> "Top This Week"
        is FeedSource.Niche -> this.name
        is FeedSource.Creator -> "@${this.username}"
        is FeedSource.Group -> this.name
        is FeedSource.Custom -> this.name
        is FeedSource.Search -> "Search: $query"
        is FeedSource.Favorites -> "Favorites"
        is FeedSource.Surprise -> "Surprise me"
    }

typealias GifItem = Gif

/** Active server sort of this source ("" = the surface's default). */
val FeedSource.activeSort: String
    get() =
        when (this) {
            is FeedSource.Search -> sort
            is FeedSource.Creator -> sort
            is FeedSource.Niche -> sort
            else -> ""
        }

/** Same feed with another server sort (§8 chips); identity sources ignore it. */
fun FeedSource.withSort(sort: String): FeedSource =
    when (this) {
        is FeedSource.Search -> copy(sort = sort)
        is FeedSource.Creator -> copy(sort = sort)
        is FeedSource.Niche -> copy(sort = sort)
        else -> this
    }

/**
 * Server sort options per feed surface — ONLY orders verified live 2026-09-30
 * (BadOrder otherwise): search {trending,latest,top7,top28,score}, creator
 * {trending,oldest,latest,top7,top28}, niche {trending,oldest,latest,best,hot}.
 * Empty sort = the surface's default (trending).
 */
fun FeedSource.sortOptions(): List<Pair<String, String>> =
    when (this) {
        is FeedSource.Search ->
            listOf(
                // Site tag/search sort wording (§8, verified 2026-10-01).
                "Latest" to "latest",
                "Top This Week" to "top7",
                "Top This Month" to "top28",
                "Most liked" to "score", // app-only; no site equivalent
            )
        is FeedSource.Creator ->
            listOf(
                // Site creator/profile sorts: Hot · Latest · Top (§8).
                "Latest" to "latest",
                "Oldest" to "oldest", // app-only
                "Top This Week" to "top7",
                "Top This Month" to "top28",
            )
        is FeedSource.Niche ->
            listOf(
                // Site niche sorts: Hot · Latest · Top; "best" is the API's Top.
                "Latest" to "latest",
                "Oldest" to "oldest", // app-only
                "Top" to "best",
                "Hot" to "hot",
            )
        else -> emptyList()
    }
