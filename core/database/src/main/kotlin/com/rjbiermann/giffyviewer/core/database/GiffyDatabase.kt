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
        LikedIdsEntity::class,
        WatchHistoryEntity::class,
        CreatorPrefEntity::class,
        TagPrefEntity::class,
        KeywordBlockEntity::class,
        HideCountEntity::class,
        CustomFeedEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
@TypeConverters(ListConverters::class, MapStringStringConverter::class)
abstract class GiffyDatabase : RoomDatabase() {
    abstract fun gifDao(): GifDao

    abstract fun feedPageDao(): FeedPageDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun tagDao(): TagDao

    abstract fun likedIdsDao(): LikedIdsDao

    abstract fun watchHistoryDao(): WatchHistoryDao

    abstract fun contentPrefsDao(): ContentPrefsDao

    abstract fun customFeedDao(): CustomFeedDao

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

        /** v4: single rolling 7-day hidden-item counter (PLAN §6). */
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `hide_counts` " +
                            "(`weekStart` INTEGER NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`weekStart`))",
                    )
                }
            }

        /** v4→5: niche groups (PLAN §6 — user-defined tag bundles; BLOCKED feeds the filter). */
        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `niche_groups` " +
                            "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                            "`tagList` TEXT NOT NULL, `state` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                    )
                }
            }

        /** v5→6: rename the likes mirror to the PLAN §5 name (`liked_ids` — the old
         *  `favorites_remote` name collides with "favorites", a different concept in §7). */
        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `favorites_remote` RENAME TO `liked_ids`")
                }
            }

        /** v7: custom feed definitions (PLAN §7 builder). */
        val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `custom_feeds` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, `sourcesJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                    )
                }
            }

        /** v9: niche names beside niche ids (picker + player chip labels — the
         *  payload already carries the names; the ids column keeps its format). */
        val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `gifs` ADD COLUMN `nicheNames` TEXT NOT NULL DEFAULT '{}'")
                }
            }

        /** v8: creator verified badge (gif payload `verified` — verified-only filter). */
        val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `gifs` ADD COLUMN `verified` INTEGER NOT NULL DEFAULT 0")
                }
            }

        /** v3: gif description caption (PLAN §9 player page). */
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `gifs` ADD COLUMN `description` TEXT")
                }
            }

        /** v10: niche groups merged into custom feeds (AGENTS-CONTENT-FILTER.md
         *  merge). custom_feeds gains the group `state`; each group becomes a
         *  custom feed whose refs are its tags (bare tag refs — the fetcher and
         *  ref-summary treat a ref without a creator:/niche: prefix as a tag
         *  search). The old group:<id> cache rows become unreachable (new feed
         *  ids) — evicted in the same migration. */
        val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `custom_feeds` ADD COLUMN `state` TEXT NOT NULL DEFAULT 'NEUTRAL'")
                    db.execSQL(
                        "INSERT INTO `custom_feeds` (`name`, `sourcesJson`, `createdAt`, `state`) " +
                            "SELECT `name`, `tagList`, `createdAt`, `state` FROM `niche_groups`",
                    )
                    db.execSQL("DROP TABLE IF EXISTS `niche_groups`")
                    db.execSQL("DELETE FROM `feed_pages` WHERE `pageKey` LIKE 'group:%'")
                }
            }
    }
}
