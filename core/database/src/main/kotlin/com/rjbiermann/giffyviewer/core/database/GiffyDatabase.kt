package com.rjbiermann.giffyviewer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * giffy.db — single source of truth (PLAN §5).
 * Schema changes ALWAYS via explicit Migration; destructive fallback is forbidden.
 */
@Database(
    entities = [
        GifEntity::class,
        FeedPageEntity::class,
        SearchHistoryEntity::class,
        TagEntity::class,
        FavoritesRemoteEntity::class,
        WatchHistoryEntity::class,
        CreatorPrefEntity::class,
        TagPrefEntity::class,
        KeywordBlockEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(ListConverters::class)
abstract class GiffyDatabase : RoomDatabase() {
    abstract fun gifDao(): GifDao

    abstract fun feedPageDao(): FeedPageDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun tagDao(): TagDao

    abstract fun favoritesRemoteDao(): FavoritesRemoteDao

    abstract fun watchHistoryDao(): WatchHistoryDao

    abstract fun contentPrefsDao(): ContentPrefsDao

    companion object {
        const val NAME = "giffy.db"

        /** v2: content controls (PLAN §6). */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `creator_prefs` (`username` TEXT NOT NULL, " +
                            "`state` TEXT NOT NULL, `changedAt` INTEGER NOT NULL, PRIMARY KEY(`username`))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `tag_prefs` (`tag` TEXT NOT NULL, `state` TEXT NOT NULL, " +
                            "`changedAt` INTEGER NOT NULL, PRIMARY KEY(`tag`))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `keyword_blocks` (`pattern` TEXT NOT NULL, " +
                            "`blockedAt` INTEGER NOT NULL, PRIMARY KEY(`pattern`))",
                    )
                }
            }

        /** v3: gif description caption (PLAN §9 player page). */
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `gifs` ADD COLUMN `description` TEXT")
                }
            }
    }
}
