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
        /** Null = the source never recorded it (v1/v2 backups, or `false`
         *  omitted by encodeDefaults=false) — import must then leave the
         *  target's own setting alone instead of forcing `false`. */
        val dataSaver: Boolean? = null,
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
        // Merge = newest edit wins (the entity keeps `changedAt` for exactly
        // this): a blind upsert let an OLDER backup re-block/re-favorite what
        // the target changed later, or resurrect a locally blocked creator.
        val localCreators = dao.allCreatorPrefs().associateBy { it.username }
        val localTags = dao.allTagPrefs().associateBy { it.tag }
        var restored = 0
        backup.creatorPrefs.forEach {
            if (it.state !in STATES) return@forEach
            val local = localCreators[it.username]
            if (local == null || it.changedAt > local.changedAt) {
                dao.upsertCreator(CreatorPrefEntity(it.username, it.state, it.changedAt))
                restored++
            }
        }
        backup.tagPrefs.forEach {
            if (it.state !in STATES) return@forEach
            val local = localTags[it.tag]
            if (local == null || it.changedAt > local.changedAt) {
                dao.upsertTag(TagPrefEntity(it.tag, it.state, it.changedAt))
                restored++
            }
        }
        // Keyword blocks match with `contains()`, so a blank pattern would hide
        // every tagged gif; an existing block stays (already blocked either way).
        val localKeywords = dao.allKeywordBlocks().map { it.pattern }.toSet()
        backup.keywordBlocks.forEach {
            val pattern = it.pattern.trim()
            if (pattern.isEmpty() || pattern in localKeywords) return@forEach
            dao.blockKeyword(KeywordBlockEntity(pattern, it.blockedAt))
            restored++
        }
        customFeedDao?.let { cfd ->
            backup.customFeeds.forEach {
                // Preserve the exported id only when it is free or already this
                // same definition (same sources = same feed, so a rename/re-state
                // updates in place). A different definition on that id is an
                // unrelated local feed from another device — upserting it would
                // REPLACE and silently destroy it, so it joins under a fresh id.
                val existing = cfd.byId(it.id)
                val sameDefinition = existing == null || existing.sourcesJson == it.sourcesJson
                cfd.upsert(
                    CustomFeedEntity(
                        id = if (sameDefinition) it.id else 0,
                        name = it.name,
                        sourcesJson = it.sourcesJson,
                        createdAt = it.createdAt,
                        state = it.state,
                    ),
                )
            }
        }
        return restored + backup.customFeeds.size
    }

    /** The only states ContentFilter understands (a mis-cased one fails open). */
    private val STATES = setOf("BLOCKED", "FAVORITED", "NEUTRAL")

    fun parseDataSaver(backupJson: String): Boolean? = json.decodeFromString<Backup>(backupJson).dataSaver
}
