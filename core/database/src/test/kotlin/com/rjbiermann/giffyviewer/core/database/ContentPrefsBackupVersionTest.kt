package com.rjbiermann.giffyviewer.core.database

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Backup version-compatibility edge cases beyond [ContentPrefsBackupTest]'s
 * round trip (gate 7): v2 exports (pre-state-field) downgrade gracefully,
 * future versions/unknown fields decode forward, custom-feed state survives,
 * and the decode-first import stays all-or-nothing per dao.
 */
class ContentPrefsBackupVersionTest {
    private class FakeCustomFeedDao : CustomFeedDao {
        val feeds = LinkedHashMap<Long, CustomFeedEntity>()

        override fun all(): kotlinx.coroutines.flow.Flow<List<CustomFeedEntity>> = kotlinx.coroutines.flow.flowOf(feeds.values.toList())

        override suspend fun byId(id: Long): CustomFeedEntity? = feeds[id]

        override suspend fun upsert(feed: CustomFeedEntity): Long {
            feeds[feed.id] = feed
            return feed.id
        }

        override suspend fun delete(id: Long) {
            feeds.remove(id)
        }

        override suspend fun blocked(): List<CustomFeedEntity> = feeds.values.filter { it.state == "BLOCKED" }
    }

    @Test
    fun `v2 export without the state field imports as NEUTRAL`() =
        runTest {
            val json =
                """{"format":"giffy-prefs","version":2,"dataSaver":true,
               "customFeeds":[{"id":7,"name":"Mix","sourcesJson":"creator:alpha,tag:spam","createdAt":5}]}"""
            val cfd = FakeCustomFeedDao()
            val restored = ContentPrefsBackup.import(FakeContentPrefsDao(), json, cfd)
            assertEquals(1, restored)
            val feed = cfd.feeds[7]!!
            assertEquals("Mix", feed.name)
            assertEquals("creator:alpha,tag:spam", feed.sourcesJson)
            assertEquals(5, feed.createdAt)
            assertEquals("v2 exports carry no state — NEUTRAL is the decoded default", "NEUTRAL", feed.state)
        }

    @Test
    fun `future version with unknown fields still imports`() =
        runTest {
            val json =
                """{"format":"giffy-prefs","version":99,"dataSaver":false,"futureVector":[1,2,3],
               "tagPrefs":[{"tag":"keep","state":"BLOCKED","changedAt":1,"changedBy":"whoever"}]}"""
            val dao = FakeContentPrefsDao()
            val restored = ContentPrefsBackup.import(dao, json)
            assertEquals(listOf("keep"), dao.blockedTags())
            assertTrue(restored == 1)
        }

    @Test
    fun `explicit custom-feed state survives the round trip`() =
        runTest {
            val source = FakeContentPrefsDao()
            val feeds =
                listOf(
                    CustomFeedEntity(1, "Blocked blend", "creator:alpha,tag:spam", 10, "BLOCKED"),
                    CustomFeedEntity(2, "Pinned blend", "niche:bbc|BBC", 20, "FAVORITED"),
                )
            val json = ContentPrefsBackup.export(source, dataSaver = false, customFeeds = feeds)

            val target = FakeContentPrefsDao()
            val cfd = FakeCustomFeedDao()
            val restored = ContentPrefsBackup.import(target, json, cfd)
            assertEquals(2, restored)
            // ids preserved so cache keys / chips keep pointing at the definition
            assertEquals("BLOCKED", cfd.feeds[1]!!.state)
            assertEquals("FAVORITED", cfd.feeds[2]!!.state)
            assertEquals(listOf(1L, 2L), cfd.feeds.keys.toList())
            // the BLOCKED feed also lands in the stage-2 blocked() query
            assertEquals(listOf(1L), cfd.blocked().map { it.id })
        }

    @Test
    fun `re-import of the same custom feed replaces in place`() =
        runTest {
            val cfd = FakeCustomFeedDao()
            val json =
                """{"format":"giffy-prefs","version":3,"dataSaver":false,
               "customFeeds":[{"id":3,"name":"v1name","sourcesJson":"tag:a","createdAt":1,"state":"NEUTRAL"}]}
                """.trimMargin()
            ContentPrefsBackup.import(FakeContentPrefsDao(), json, cfd)
            val renamed = json.replace("v1name", "v2name")
            ContentPrefsBackup.import(FakeContentPrefsDao(), renamed, cfd)
            assertEquals(1, cfd.feeds.size)
            assertEquals("v2name", cfd.feeds[3]!!.name)
        }

    @Test
    fun `parseDataSaver reads the flag`() {
        assertTrue(ContentPrefsBackup.parseDataSaver("""{"format":"giffy-prefs","version":3,"dataSaver":true}"""))
        assertTrue(
            "defaults apply when the flag is absent",
            !ContentPrefsBackup.parseDataSaver("""{"format":"giffy-prefs","version":3}"""),
        )
    }

    @Test
    fun `import with no sections restores zero entries and touches nothing`() =
        runTest {
            val cfd = FakeCustomFeedDao()
            assertEquals(0, ContentPrefsBackup.import(FakeContentPrefsDao(), """{"format":"giffy-prefs","version":3}""", cfd))
            assertTrue(cfd.feeds.isEmpty())
        }

    @Test
    fun `malformed custom-feed entry throws before ANY dao write`() =
        runTest {
            val dao = FakeContentPrefsDao()
            dao.upsertCreator(CreatorPrefEntity("existing", "BLOCKED", 0))
            val cfd = FakeCustomFeedDao()
            val before = dao.allCreatorPrefs()
            // decode happens FIRST (before any upsert) → partial imports impossible
            val json =
                """{"format":"giffy-prefs","version":3,"dataSaver":false,
               "customFeeds":[{"name":"no id or sources"}]}"""
            try {
                ContentPrefsBackup.import(dao, json, cfd)
                org.junit.Assert.fail("must throw on a structurally invalid feed row")
            } catch (_: Exception) {
            }
            assertEquals(before, dao.allCreatorPrefs())
            assertTrue(cfd.feeds.isEmpty())
        }
}
