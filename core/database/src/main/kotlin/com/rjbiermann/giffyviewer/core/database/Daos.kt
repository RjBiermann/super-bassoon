package com.rjbiermann.giffyviewer.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GifDao {
    /** "Surprise me" (§8): random unwatched cached gifs — excludes watch history. */
    @Query(
        "SELECT * FROM gifs " +
            "WHERE id NOT IN (SELECT gifId FROM watch_history) " +
            "ORDER BY RANDOM() LIMIT :limit",
    )
    suspend fun randomUnwatched(limit: Int): List<GifEntity>

    @Upsert
    suspend fun upsertAll(gifs: List<GifEntity>)

    @Query("SELECT * FROM gifs WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<GifEntity>

    @Query("SELECT * FROM gifs WHERE id = :id")
    suspend fun byId(id: String): GifEntity?

    @Query("SELECT * FROM gifs WHERE id = :id")
    fun byIdFlow(id: String): Flow<GifEntity?>

    @Query("SELECT COUNT(*) FROM gifs")
    fun countFlow(): Flow<Int>
}

@Dao
interface FeedPageDao {
    @Upsert
    suspend fun upsert(page: FeedPageEntity)

    @Query("SELECT * FROM feed_pages WHERE pageKey = :pageKey")
    suspend fun page(pageKey: String): FeedPageEntity?

    @Query("SELECT * FROM feed_pages WHERE pageKey = :pageKey")
    fun pageFlow(pageKey: String): Flow<FeedPageEntity?>

    @Transaction
    suspend fun pageWithGifs(pageKey: String): Pair<FeedPageEntity, List<GifEntity>>? {
        val p = page(pageKey) ?: return null
        return p to gifsByIds(p.gifIds)
    }

    @Query("SELECT * FROM gifs WHERE id IN (:ids)")
    suspend fun gifsByIds(ids: List<String>): List<GifEntity>

    @Query("SELECT * FROM feed_pages WHERE pageKey LIKE :keyBase || ':p%'")
    suspend fun pagesForBase(keyBase: String): List<FeedPageEntity>

    @Query("DELETE FROM feed_pages WHERE fetchedAt < :olderThan")
    suspend fun evictStale(olderThan: Long)

    @Query("DELETE FROM feed_pages WHERE pageKey LIKE :base || ':p%'")
    suspend fun evictBase(base: String)

    /** Favorites pool eviction (2026-10 user report: favoriting a creator
     *  blanked the Favorites feed until the 10-min TTL): a favorites-set
     *  change (quick-toggle, Settings unfavorite, backup import) invalidates
     *  the cached round-robin pages — they were built against the OLD set, so
     *  the read-time favorites filter dropped every row. Evicting makes the
     *  next load self-fill against the NEW set. The prefix pins "fav:v1"
     *  (FeedSource.Favorites.keyBase — the feature:feed contract test guards
     *  drift); the key lives here because every favorites-write module reaches
     *  this DAO (feature:settings has no feature:feed dependency). */
    @Query("DELETE FROM feed_pages WHERE pageKey LIKE :prefix || '%'")
    suspend fun evictFavorites(prefix: String = FAVORITES_PAGE_PREFIX)
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entry: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history")
    suspend fun clear()

    /** Per-row remove (tap the ✕ on a history row). */
    @Query("DELETE FROM search_history WHERE query = :query COLLATE NOCASE")
    suspend fun remove(query: String)

    /** Keep the newest :cap entries (PLAN §5 — oldest evicted on insert). */
    @Query(
        "DELETE FROM search_history WHERE id NOT IN " +
            "(SELECT id FROM search_history ORDER BY searchedAt DESC LIMIT :cap)",
    )
    suspend fun trim(cap: Int)
}

@Dao
interface TagDao {
    @Upsert
    suspend fun upsertAll(tags: List<TagEntity>)

    @Query("SELECT * FROM tags WHERE name LIKE '%' || :prefix || '%' ORDER BY name LIMIT :limit")
    fun suggest(
        prefix: String,
        limit: Int,
    ): Flow<List<TagEntity>>

    @Query("SELECT COUNT(*) FROM tags WHERE fetchedAt > :newerThan")
    suspend fun freshCount(newerThan: Long): Int
}

@Dao
interface LikedIdsDao {
    @Upsert
    suspend fun replaceAll(items: List<LikedIdsEntity>)

    @Query("SELECT * FROM liked_ids ORDER BY syncedAt DESC")
    fun allFlow(): Flow<List<LikedIdsEntity>>

    @Query("SELECT gifId FROM liked_ids")
    suspend fun allIds(): List<String>

    @Upsert
    suspend fun upsert(item: LikedIdsEntity)

    @Query("DELETE FROM liked_ids WHERE gifId = :gifId")
    suspend fun clearById(gifId: String)

    @Query("DELETE FROM liked_ids")
    suspend fun clear()
}

@Dao
interface WatchHistoryDao {
    @Upsert
    suspend fun upsert(entry: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE gifId = :gifId")
    suspend fun byGif(gifId: String): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history WHERE watched = 1 ORDER BY updatedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<WatchHistoryEntity>>

    /** Partially watched — the TV "Continue Watching" row (PLAN §8). */
    @Query("SELECT * FROM watch_history WHERE watched = 0 AND positionMs > 0 ORDER BY updatedAt DESC LIMIT :limit")
    fun continueWatching(limit: Int): Flow<List<WatchHistoryEntity>>
}

@Dao
interface ContentPrefsDao {
    @Upsert
    suspend fun upsertCreator(pref: CreatorPrefEntity)

    @Upsert
    suspend fun upsertTag(pref: TagPrefEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockKeyword(pattern: KeywordBlockEntity)

    @Query("SELECT username FROM creator_prefs WHERE state = 'BLOCKED'")
    suspend fun blockedCreators(): List<String>

    @Query("SELECT * FROM creator_prefs")
    suspend fun allCreatorPrefs(): List<CreatorPrefEntity>

    @Query("SELECT username FROM creator_prefs WHERE state = 'FAVORITED'")
    suspend fun favoriteCreators(): List<String>

    @Query("SELECT tag FROM tag_prefs WHERE state = 'BLOCKED'")
    suspend fun blockedTags(): List<String>

    @Query("SELECT * FROM tag_prefs")
    suspend fun allTagPrefs(): List<TagPrefEntity>

    @Query("SELECT pattern FROM keyword_blocks")
    suspend fun blockedKeywords(): List<String>

    @Query("SELECT * FROM keyword_blocks")
    suspend fun allKeywordBlocks(): List<KeywordBlockEntity>

    @Query("DELETE FROM creator_prefs WHERE username = :username")
    suspend fun unblockCreator(username: String)

    @Query("DELETE FROM tag_prefs WHERE tag = :tag")
    suspend fun clearTag(tag: String)

    @Query("DELETE FROM tag_prefs WHERE state = 'FAVORITED' AND tag = :tag")
    suspend fun unfavoriteTag(tag: String)

    @Query("DELETE FROM keyword_blocks WHERE pattern = :pattern")
    suspend fun unblockKeyword(pattern: String)

    @Query("SELECT username FROM creator_prefs WHERE state = 'BLOCKED' ORDER BY username")
    fun blockedCreatorsFlow(): Flow<List<String>>

    @Query("SELECT username FROM creator_prefs WHERE state = 'FAVORITED' ORDER BY username")
    fun favoriteCreatorsFlow(): Flow<List<String>>

    @Query("SELECT state FROM creator_prefs WHERE username = :username")
    fun creatorState(username: String): Flow<String?>

    @Query("SELECT state FROM tag_prefs WHERE tag = :tag")
    fun tagState(tag: String): Flow<String?>

    @Query("SELECT tag FROM tag_prefs WHERE state = 'FAVORITED' ORDER BY tag")
    fun favoriteTagsFlow(): Flow<List<String>>

    @Query("SELECT tag FROM tag_prefs WHERE state = 'BLOCKED' ORDER BY tag")
    fun blockedTagsFlow(): Flow<List<String>>

    @Query("SELECT pattern FROM keyword_blocks ORDER BY pattern")
    fun blockedKeywordsFlow(): Flow<List<String>>

    @Query("SELECT count FROM hide_counts WHERE weekStart = :weekStart")
    fun hideCount(weekStart: Long): Flow<Int?>

    @Query(
        "INSERT INTO hide_counts(weekStart, count) VALUES(:weekStart, :delta) " +
            "ON CONFLICT(weekStart) DO UPDATE SET count = count + :delta",
    )
    suspend fun addHideCount(
        weekStart: Long,
        delta: Int,
    )
}

@Dao
interface CustomFeedDao {
    @Query("SELECT * FROM custom_feeds ORDER BY createdAt")
    fun all(): Flow<List<CustomFeedEntity>>

    @Query("SELECT * FROM custom_feeds WHERE id = :id")
    suspend fun byId(id: Long): CustomFeedEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(feed: CustomFeedEntity): Long

    @Query("DELETE FROM custom_feeds WHERE id = :id")
    suspend fun delete(id: Long)

    /** Stage 2 (§6): BLOCKED feeds drive the ContentFilter macro-blocks. */
    @Query("SELECT * FROM custom_feeds WHERE state = 'BLOCKED'")
    suspend fun blocked(): List<CustomFeedEntity>
}
