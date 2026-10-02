package com.rjbiermann.giffyviewer.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** PLAN §5: metadata incl. tags + username stored locally for offline filtering. */
@Entity(tableName = "gifs")
data class GifEntity(
    @PrimaryKey val id: String,
    val userName: String,
    val description: String?,
    val tags: List<String>,
    val niches: List<String>,
    /** Niche names keyed by id (payload carries them; Room column, DB v9). */
    val nicheNames: Map<String, String> = emptyMap(),
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
    val verified: Boolean,
    val fetchedAt: Long,
)

/** PLAN §5: cache keys like "trend:v2:pop:p3". Ordered gif ids reference [GifEntity]. */
@Entity(tableName = "feed_pages")
data class FeedPageEntity(
    @PrimaryKey val pageKey: String,
    val gifIds: List<String>,
    /** "trend:v2:pop:p4" — null means end of pagination. */
    val nextPageKey: String?,
    val fetchedAt: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val searchedAt: Long,
)

/** Tag cache for autocomplete; TTL 7 days (PLAN §5). */
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val name: String,
    val fetchedAt: Long,
)

/** Mirror of the account's liked gif ids — refreshed per use, never TTL-cached (PLAN §5). */
@Entity(tableName = "liked_ids")
data class LikedIdsEntity(
    @PrimaryKey val gifId: String,
    val syncedAt: Long,
)

/** Resume positions + "surprise me" exclusion set (PLAN §5, §8). */
@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val gifId: String,
    val positionMs: Long,
    val updatedAt: Long,
    val watched: Boolean,
)

/** Content controls (PLAN §6): state ∈ BLOCKED | FAVORITED. */
@Entity(tableName = "creator_prefs")
data class CreatorPrefEntity(
    @PrimaryKey val username: String,
    val state: String,
    val changedAt: Long,
)

@Entity(tableName = "tag_prefs")
data class TagPrefEntity(
    @PrimaryKey val tag: String,
    val state: String,
    val changedAt: Long,
)

/** Case-insensitive substring blocks (PLAN §6). */
@Entity(tableName = "keyword_blocks")
data class KeywordBlockEntity(
    @PrimaryKey val pattern: String,
    val blockedAt: Long,
)

/** Rolling 7-day hidden-item counter (PLAN §6 revised 2026-09-30: single counter). */
@Entity(tableName = "hide_counts")
data class HideCountEntity(
    @PrimaryKey val weekStart: Long,
    val count: Int,
)

/** Niche group (PLAN §6): user-defined tag bundle; full feeds + macro-filter. */
@Entity(tableName = "niche_groups")
data class NicheGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Comma-separated tag list (matches the site's groups storage shape). */
    val tagList: String,
    /** BLOCKED | FAVORITED | NEUTRAL. */
    val state: String,
    val createdAt: Long,
)

/**
 * Custom feed definition (PLAN §7): named blend of creators, groups and single
 * tags. sources_json = app-side JSON list [{type:"creator"|"group"|"tag", ref}].
 */
@Entity(tableName = "custom_feeds")
data class CustomFeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourcesJson: String,
    val createdAt: Long,
)
