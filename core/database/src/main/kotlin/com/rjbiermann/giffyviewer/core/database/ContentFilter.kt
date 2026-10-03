package com.rjbiermann.giffyviewer.core.database

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * THE content-controls choke point (PLAN §6, AGENTS-CONTENT-FILTER.md). Every
 * data source runs items through [hideReason] before UI. Sets are session-cached
 * and reloaded on demand; the FeedPagingSource invalidation observer triggers
 * reloads when prefs change.
 *
 * Leak-zero invariant: a gif with a non-null [hideReason] must never render.
 */
@javax.inject.Singleton
class ContentFilter
    @javax.inject.Inject
    constructor() {
        private val mutex = Mutex()

        @Volatile
        private var creators: Set<String> = emptySet()

        @Volatile
        private var tags: Set<String> = emptySet()

        @Volatile
        private var keywords: List<String> = emptyList()

        @Volatile
        private var feedTags: Set<String> = emptySet()

        suspend fun refreshFrom(dao: ContentPrefsDao) {
            mutex.withLock {
                creators =
                    dao.blockedCreators().mapTo(HashSet()) { it.lowercase() }
                tags =
                    dao.blockedTags().mapTo(HashSet()) { it.lowercase() }
                keywords =
                    dao.blockedKeywords().map { it.lowercase() }
            }
        }

        /** Stage 2 (§6, post-merge): every BLOCKED custom feed's refs join the
         *  global block sets — tag refs (bare legacy or "tag:"-prefixed) → the
         *  tag set; "creator:" refs → the creator set. "niche:" refs are
         *  skipped: a niche's tag list isn't local, so mapping it to tags would
         *  be a guess. MUST run AFTER [refreshFrom] (it merges, not replaces,
         *  the creator set). Called alongside it by the paging source (one
         *  session reload). */
        suspend fun refreshBlockedFeeds(dao: CustomFeedDao) {
            val blocked = dao.blocked()
            val tags = HashSet<String>()
            val extraCreators = HashSet<String>()
            for (feed in blocked) {
                for (ref in feed.sourcesJson.split(',')) {
                    val r = ref.trim()
                    when {
                        r.isEmpty() -> Unit
                        r.startsWith("creator:") -> extraCreators.add(r.removePrefix("creator:").lowercase())
                        r.startsWith("niche:") -> Unit
                        else -> tags.add(r.removePrefix("tag:").lowercase())
                    }
                }
            }
            mutex.withLock {
                feedTags = tags
                creators = creators + extraCreators
            }
        }

        /** Null = allow. Otherwise the reason: "creator" | "tag" | "feed" | "keyword"
         *  | "unverified". Keyword = case-insensitive substring over description +
         *  tags (PLAN §6: title/description/tags — gif objects carry no title today).
         *  Verified-only is a read-time PREF like orientation — never a block, so
         *  callers must not count its rejections in hide counts. */
        fun hideReason(
            userName: String,
            gifTags: List<String>,
            description: String? = null,
            gifVerified: Boolean = false,
            verifiedOnly: Boolean = false,
        ): String? {
            if (verifiedOnly && !gifVerified) return "unverified"
            if (userName.lowercase() in creators) return "creator"
            if (gifTags.any { it.lowercase() in tags }) return "tag"
            if (gifTags.any { it.lowercase() in feedTags }) return "feed"
            if (keywords.any { k -> gifTags.any { it.lowercase().contains(k) } }) return "keyword"
            if (description != null && keywords.any { description.lowercase().contains(it) }) return "keyword"
            return null
        }

        fun allow(
            userName: String,
            gifTags: List<String>,
            description: String? = null,
            gifVerified: Boolean = false,
            verifiedOnly: Boolean = false,
        ): Boolean = hideReason(userName, gifTags, description, gifVerified, verifiedOnly) == null
    }

/** Start of the 7-day bucket containing [nowEpochMs] (PLAN §6 rolling counter). */
fun weekStartMs(nowEpochMs: Long): Long = nowEpochMs / WEEK_MS * WEEK_MS

internal const val WEEK_MS: Long = 7 * 24 * 60 * 60 * 1_000L
