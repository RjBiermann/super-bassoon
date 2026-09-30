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
    data class Backup(
        val format: String = "giffy-prefs",
        val version: Int = 1,
        val dataSaver: Boolean = false,
        val creatorPrefs: List<CreatorPref> = emptyList(),
        val tagPrefs: List<TagPref> = emptyList(),
        val keywordBlocks: List<KeywordBlock> = emptyList(),
    )

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    suspend fun export(
        dao: ContentPrefsDao,
        dataSaver: Boolean,
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
            ),
        )

    /** @return number of restored entries, or throws on malformed input. */
    suspend fun import(
        dao: ContentPrefsDao,
        backupJson: String,
    ): Int {
        val backup = json.decodeFromString<Backup>(backupJson)
        backup.creatorPrefs.forEach { dao.upsertCreator(CreatorPrefEntity(it.username, it.state, it.changedAt)) }
        backup.tagPrefs.forEach { dao.upsertTag(TagPrefEntity(it.tag, it.state, it.changedAt)) }
        backup.keywordBlocks.forEach { dao.blockKeyword(KeywordBlockEntity(it.pattern, it.blockedAt)) }
        return backup.creatorPrefs.size + backup.tagPrefs.size + backup.keywordBlocks.size
    }

    fun parseDataSaver(backupJson: String): Boolean = json.decodeFromString<Backup>(backupJson).dataSaver
}
