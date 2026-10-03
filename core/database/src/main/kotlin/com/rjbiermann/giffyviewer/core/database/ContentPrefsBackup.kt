package com.rjbiermann.giffyviewer.core.database

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Export/import of all content controls as JSON (PLAN §6 Settings → export/import,
 * gate 7: must round-trip on a fresh install). Merge-upsert on import — a fresh
 * install is empty anyway, and merging never destroys the target's newer edits.
 */
object ContentPrefsBackup {
    @Serializable
    data class CreatorPref(
        val username: String,
        val state: String,
        val changedAt: Long,
    )

    @Serializable
    data class TagPref(
        val tag: String,
        val state: String,
        val changedAt: Long,
    )

    @Serializable
    data class KeywordBlock(
        val pattern: String,
        val blockedAt: Long,
    )

    @Serializable
    data class CustomFeedDef(
        val id: Long,
        val name: String,
        /** Comma-joined refs ("creator:<u>" / "tag:<text>" / "niche:<id>|<name>";
         *  legacy bare tags count as tag refs). */
        val sourcesJson: String,
        val createdAt: Long,
        /** v3: the merged group state (BLOCKED | FAVORITED | NEUTRAL) — absent
         *  in v2 exports, decodes to NEUTRAL. */
        val state: String = "NEUTRAL",
    )

    @Serializable
    data class Backup(
        val format: String = "giffy-prefs",
        val version: Int = 3,
        val dataSaver: Boolean = false,
        val creatorPrefs: List<CreatorPref> = emptyList(),
        val tagPrefs: List<TagPref> = emptyList(),
        val keywordBlocks: List<KeywordBlock> = emptyList(),
        val customFeeds: List<CustomFeedDef> = emptyList(),
    )

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    suspend fun export(
        dao: ContentPrefsDao,
        dataSaver: Boolean,
        customFeeds: List<CustomFeedEntity> = emptyList(),
    ): String =
        json.encodeToString(
            Backup(
                dataSaver = dataSaver,
                creatorPrefs =
                    dao.allCreatorPrefs().map {
                        CreatorPref(it.username, it.state, it.changedAt)
                    },
                tagPrefs =
                    dao.allTagPrefs().map {
                        TagPref(it.tag, it.state, it.changedAt)
                    },
                keywordBlocks =
                    dao.allKeywordBlocks().map {
                        KeywordBlock(it.pattern, it.blockedAt)
                    },
                customFeeds =
                    customFeeds.map {
                        CustomFeedDef(it.id, it.name, it.sourcesJson, it.createdAt, it.state)
                    },
            ),
        )

    /** @return number of restored entries, or throws on malformed input. */
    suspend fun import(
        dao: ContentPrefsDao,
        backupJson: String,
        customFeedDao: CustomFeedDao? = null,
    ): Int {
        val backup = json.decodeFromString<Backup>(backupJson)
        backup.creatorPrefs.forEach { dao.upsertCreator(CreatorPrefEntity(it.username, it.state, it.changedAt)) }
        backup.tagPrefs.forEach { dao.upsertTag(TagPrefEntity(it.tag, it.state, it.changedAt)) }
        backup.keywordBlocks.forEach { dao.blockKeyword(KeywordBlockEntity(it.pattern, it.blockedAt)) }
        customFeedDao?.let { cfd ->
            backup.customFeeds.forEach {
                cfd.upsert(
                    CustomFeedEntity(
                        // preserve the export's id so existing custom-feed chips/
                        // cache keys keep pointing at the same definition
                        id = it.id,
                        name = it.name,
                        sourcesJson = it.sourcesJson,
                        createdAt = it.createdAt,
                        state = it.state,
                    ),
                )
            }
        }
        return backup.creatorPrefs.size + backup.tagPrefs.size + backup.keywordBlocks.size + backup.customFeeds.size
    }

    fun parseDataSaver(backupJson: String): Boolean = json.decodeFromString<Backup>(backupJson).dataSaver
}
