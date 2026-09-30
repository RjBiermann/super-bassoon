package com.rjbiermann.giffyviewer.core.database

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gate 7: export → import must round-trip on a fresh (empty) install. */
class ContentPrefsBackupTest {
    @Test
    fun `export import round trip on empty dao`() =
        runTest {
            val source = FakeContentPrefsDao()
            source.upsertCreator(CreatorPrefEntity("emily.reed", "BLOCKED", 1))
            source.upsertCreator(CreatorPrefEntity("sweetiefox", "FAVORITED", 2))
            source.upsertTag(TagPrefEntity("onlyfans", "BLOCKED", 3))
            source.blockKeyword(KeywordBlockEntity("big tits", 4))

            val json = ContentPrefsBackup.export(source, dataSaver = true)

            // Fresh install: empty dao receives the import.
            val target = FakeContentPrefsDao()
            val restored = ContentPrefsBackup.import(target, json)
            assertEquals(4, restored)

            assertEquals(source.allCreatorPrefs(), target.allCreatorPrefs())
            assertEquals(source.allTagPrefs(), target.allTagPrefs())
            assertEquals(source.allKeywordBlocks(), target.allKeywordBlocks())
        }

    @Test
    fun `import is merge-upsert and survives unknown fields`() =
        runTest {
            val json =
                """{"format":"giffy-prefs","version":1,"dataSaver":false,
               "futureField":true,
               "creatorPrefs":[{"username":"a","state":"BLOCKED","changedAt":9}]}"""
            val dao = FakeContentPrefsDao()
            dao.upsertTag(TagPrefEntity("keep", "BLOCKED", 1))
            ContentPrefsBackup.import(dao, json)
            assertEquals(listOf("a"), dao.blockedCreators())
            assertEquals(listOf("keep"), dao.blockedTags())
        }

    @Test
    fun `malformed input throws not corrupts`() =
        runTest {
            val dao = FakeContentPrefsDao()
            val before = dao.allCreatorPrefs()
            try {
                ContentPrefsBackup.import(dao, "not json")
                assertTrue(false)
            } catch (_: Exception) {
            }
            assertEquals(before, dao.allCreatorPrefs())
        }
}
