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
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entry: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history")
    suspend fun clear()
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
interface FavoritesRemoteDao {
    @Upsert
    suspend fun replaceAll(items: List<FavoritesRemoteEntity>)

    @Query("SELECT * FROM favorites_remote ORDER BY syncedAt DESC")
    fun allFlow(): Flow<List<FavoritesRemoteEntity>>

    @Query("SELECT gifId FROM favorites_remote")
    suspend fun allIds(): List<String>

    @Query("DELETE FROM favorites_remote")
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
    suspend fun unblockTag(tag: String)

    @Query("DELETE FROM keyword_blocks WHERE pattern = :pattern")
    suspend fun unblockKeyword(pattern: String)

    @Query("SELECT username FROM creator_prefs WHERE state = 'BLOCKED' ORDER BY username")
    fun blockedCreatorsFlow(): Flow<List<String>>

    @Query("SELECT username FROM creator_prefs WHERE state = 'FAVORITED' ORDER BY username")
    fun favoriteCreatorsFlow(): Flow<List<String>>

    @Query("SELECT state FROM creator_prefs WHERE username = :username")
    fun creatorState(username: String): Flow<String?>

    @Query("SELECT tag FROM tag_prefs WHERE state = 'BLOCKED' ORDER BY tag")
    fun blockedTagsFlow(): Flow<List<String>>

    @Query("SELECT pattern FROM keyword_blocks ORDER BY pattern")
    fun blockedKeywordsFlow(): Flow<List<String>>
}
