package com.rjbiermann.giffyviewer.feature.feed

import kotlinx.serialization.Serializable

/**
 * Feed identity = cache key base (PLAN §7). Sort/range changes produce distinct
 * bases ("trend:v2:pop:sort=top_week"), client-side ops don't.
 *
 * @Serializable carries the subtypes as nav route/saved-state payload (issue
 * #13 §2 the route JSON lives only inside nav saved state, never a public
 * format). Keyed members (keyBase/baseKey/ttlMs) are computed getters, NOT
 * serializable fields — a route arg is exactly the subtype's constructor
 * params (derived members re-derive from them on decode).
 */
@Serializable
sealed interface FeedSource {
    /** Base cache key for page keys "base:p<n>". */
    val keyBase: String

    /** Sortless base (DataStore per-feed sort persistence keys on this). */
    val baseKey: String get() = keyBase

    /** TTL in ms: REFRESH re-hits the API only when stale (PLAN §5). */
    val ttlMs: Long

    @Serializable
    data object Trending : FeedSource {
        override val keyBase: String get() = "trend:v2:pop"
        override val ttlMs: Long get() = TTL_TRENDING
    }

    @Serializable
    data class Search(
        val query: String,
        val sort: String = "",
    ) : FeedSource {
        override val keyBase: String get() = "search:${query.lowercase().trim()}" + if (sort.isEmpty()) "" else ":sort=$sort"
        override val baseKey: String get() = "search:${query.lowercase().trim()}"
        override val ttlMs: Long get() = TTL_SEARCH
    }

    /** Niche feed — v2/niches/{id}/gifs (live-verified, anonymous OK). */
    @Serializable
    data class Niche(
        val id: String,
        val name: String,
        val sort: String = "",
    ) : FeedSource {
        override val keyBase: String get() = "niche:$id" + if (sort.isEmpty()) "" else ":sort=$sort"
        override val baseKey: String get() = "niche:$id"
        override val ttlMs: Long get() = TTL_SEARCH
    }

    /** Top This Week = trending/popular?order=top_week (live-verified). */
    @Serializable
    data object TopThisWeek : FeedSource {
        override val keyBase: String get() = "topweek:v1"
        override val ttlMs: Long get() = TTL_TRENDING
    }

    /** Site-wide ranged top pool (issue #11, live-verified 2026-10-15): a
     *  BARE gifs/search (no query) whose order is one of the endpoint's
     *  verified top set — top7 = week, top28 = month, top = all-time
     *  (top_week/top_month/etc. are BadOrder here; that wording only lives
     *  on trending/popular). Feeds through the standard cache-first pager. */
    @Serializable
    data class TopRanged(
        val range: String,
    ) : FeedSource {
        init {
            require(range in RANGES) { "unverified top range: $range" }
        }

        override val keyBase: String get() = "top:$range"
        override val ttlMs: Long get() = TTL_TRENDING

        companion object {
            /** The search endpoint's BadOrder-verified top orders (2026-10-15). */
            val RANGES = listOf("top7", "top28", "top")
        }
    }

    /** Logged-in personalized personalized server feed (PLAN §7); chip hidden when logged out. */
    @Serializable
    data object ForYou : FeedSource {
        override val keyBase: String get() = "foryou:v1"
        override val ttlMs: Long get() = TTL_TRENDING
    }

    /** Recency round-robin over favorited creators (PLAN §7). The cache base
     *  carries the mapping generation: #62 changed the page→content mapping
     *  twice (v3 = window-independent per-creator slices), and #65 changed what
     *  every creator sub-fetch asks for (order `latest` instead of the empty
     *  `trending` pool), so rows written by an older mapping generation can't
     *  be served as this one's pages (#59 lesson: mapping changed, keys
     *  didn't). */
    @Serializable
    data object Favorites : FeedSource {
        override val keyBase: String get() = "fav:v4"

        /** Prefs identity stays put across cache-generation bumps. */
        override val baseKey: String get() = "fav:v1"
        override val ttlMs: Long get() = TTL_TRENDING
    }

    /** Single creator feed — v2/users/{username}/search (same call the
     *  Favorites round-robin already uses). Pinned creator tabs (PLAN §7
     *  pin-to-tabs). The cache base carries the mapping generation: #65 changed
     *  the page→content order (see [CREATOR_DEFAULT_ORDER]), so pages cached
     *  under the old `user:` base must not be served as this one's. */
    @Serializable
    data class Creator(
        val username: String,
        val sort: String = "",
    ) : FeedSource {
        override val keyBase: String get() = "user:v2:${username.lowercase().trim()}" + if (sort.isEmpty()) "" else ":sort=$sort"
        override val baseKey: String get() = "user:${username.lowercase().trim()}"
        override val ttlMs: Long get() = TTL_SEARCH
    }

    /** Custom feed (PLAN §7 builder): named blend of creators/tags/niches,
     *  fetched round-robin — page n maps to refs[(n-1) % n] with inner page
     *  (n-1)/n + 1. Ref types: "creator:<username>" · "tag:<text>" (bare legacy
     *  tags from the v10 group migration count as tag refs) · "niche:<id>|<name>".
     *  Also carries the merged niche-group role via the DB `state` column:
     *  FAVORITED feeds pin as home tabs, BLOCKED feeds drive the ContentFilter. */
    @Serializable
    data class Custom(
        val id: Long,
        val name: String,
        val refs: List<String>,
    ) : FeedSource {
        override val keyBase: String get() = "custom:$id"
        override val ttlMs: Long get() = TTL_SEARCH
    }

    /** Liked gifs (PLAN §7, logged-in): network-LIVE — never cached (§5);
     *  pages have no cache keys and sorts are client-side only. */
    @Serializable
    data object Liked : FeedSource {
        override val keyBase: String get() = "liked:v1"
        override val ttlMs: Long get() = 0L
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

/** Issue #31 niche-header gate (pure, unit-tested): only a niche feed owns the
 *  niche stats header; every other source maps to null = the clear call
 *  (refreshNicheStats(null) clears the StateFlow exactly like the creator
 *  header's null gate does). */
fun nicheSourceId(source: FeedSource): String? = (source as? FeedSource.Niche)?.id

/** A feed's UI-facing identity for titles. */
fun FeedSource.title(): String =
    when (this) {
        is FeedSource.Trending -> "Trending"
        is FeedSource.ForYou -> "For You"
        is FeedSource.Liked -> "Liked GIFs & Images"
        is FeedSource.TopThisWeek -> "Top This Week"
        is FeedSource.TopRanged ->
            when (this.range) {
                "top7" -> "Top This Week"
                "top28" -> "Top This Month"
                else -> "Top of All Time"
            }
        is FeedSource.Niche -> this.name
        is FeedSource.Creator -> "@${this.username}"
        is FeedSource.Custom -> this.name
        is FeedSource.Search -> "Search: $query"
        is FeedSource.Favorites -> "Favorites"
    }

/** Active server sort of this source ("" = the surface's default). */
val FeedSource.activeSort: String
    get() =
        when (this) {
            is FeedSource.Search -> sort
            is FeedSource.Creator -> sort
            is FeedSource.Niche -> sort
            else -> ""
        }

/** The order this surface fetches with when no sort is chosen (#65: the
 *  creator endpoint's `trending` subset is empty for plenty of creators, so
 *  its default is the explicit [CREATOR_DEFAULT_ORDER]). The sort chip falls
 *  back to it, so the default view highlights the chip it actually serves. */
val FeedSource.defaultSort: String
    get() =
        when (this) {
            is FeedSource.Creator -> CREATOR_DEFAULT_ORDER
            else -> ""
        }

/** The feed with [sort] applied — the identity the cache and the fetch share. */
fun FeedSource.withSort(sort: String): FeedSource =
    when (this) {
        is FeedSource.Search -> copy(sort = sort)
        is FeedSource.Creator -> copy(sort = sort)
        is FeedSource.Niche -> copy(sort = sort)
        else -> this
    }

/** Cache base a sort change is owed a one-shot TTL bypass for: the POST-sort
 *  identity — exactly the key the gifs flow reads off the feed it opens
 *  (FeedViewModel.gifs). The sortless [FeedSource.baseKey] is never it for a
 *  sortable surface (their keyBase carries the `:sort=` suffix). */
fun FeedSource.sortForceKey(sort: String): String = withSort(sort).keyBase

/**
 * Server sort options per feed surface — ONLY orders verified live (BadOrder
 * otherwise): search {trending,latest,top7,top28,top(2026-10-15),score},
 * creator {trending,oldest,latest,top7,top28}, niche
 * {trending,oldest,latest,best,hot}. Empty sort = the surface's default order
 * ([defaultSort]: trending for search/niche, latest for the creator feed —
 * `trending` there is a subset an inactive creator answers empty, #65).
 * `trending` ships no chip: `""` already selects it where it is the default.
 */
fun FeedSource.sortOptions(): List<Pair<String, String>> =
    when (this) {
        is FeedSource.Search ->
            listOf(
                // Site tag/search sort wording (§8, verified 2026-10-01);
                // "top" = all-time (BadOrder-verified 2026-10-15, issue #11).
                "Latest" to "latest",
                "Top This Week" to "top7",
                "Top This Month" to "top28",
                "Top of All Time" to "top",
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
