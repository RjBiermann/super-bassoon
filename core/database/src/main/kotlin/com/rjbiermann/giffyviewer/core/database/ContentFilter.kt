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
        private var groupTags: Set<String> = emptySet()

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

        /** Stage 2 (§6): every BLOCKED group's tags join the global block set.
         *  Called alongside [refreshFrom] by the paging source (one session reload). */
        suspend fun refreshGroupTags(dao: NicheGroupDao) {
            val merged =
                dao.blocked().flatMapTo(HashSet()) { g ->
                    g.tagList.split(',').mapNotNull { t -> t.trim().lowercase().takeIf(String::isNotEmpty) }
                }
            mutex.withLock { groupTags = merged }
        }

        /** Null = allow. Otherwise the reason: "creator" | "tag" | "group" | "keyword". */
        fun hideReason(
            userName: String,
            gifTags: List<String>,
        ): String? {
            if (userName.lowercase() in creators) return "creator"
            if (gifTags.any { it.lowercase() in tags }) return "tag"
            if (gifTags.any { it.lowercase() in groupTags }) return "group"
            // upstream gif objects carry no title/description — tags are the only text.
            if (keywords.any { k -> gifTags.any { it.lowercase().contains(k) } }) return "keyword"
            return null
        }

        fun allow(
            userName: String,
            gifTags: List<String>,
        ): Boolean = hideReason(userName, gifTags) == null
    }

/** Start of the 7-day bucket containing [nowEpochMs] (PLAN §6 rolling counter). */
fun weekStartMs(nowEpochMs: Long): Long = nowEpochMs / WEEK_MS * WEEK_MS

internal const val WEEK_MS: Long = 7 * 24 * 60 * 60 * 1_000L
