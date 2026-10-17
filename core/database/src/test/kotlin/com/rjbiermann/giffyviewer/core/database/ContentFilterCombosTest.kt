package com.rjbiermann.giffyviewer.core.database

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Combination + edge-case rules of the ContentFilter choke point beyond
 * [ContentFilterTest]'s single-cause happy paths: verified-only precedence,
 * feed-vs-tag reason precedence, stage-2 merge ordering, literal (not regex)
 * keyword matching, multi-keyword any-match, and the leak-zero contract that
 * `allow()` mirrors `hideReason()` exactly.
 */
class ContentFilterCombosTest {
    private val gifTags = listOf("Bigger", "Femboy", "NSFW")

    /** Direct-list prime (issue #42 overload) — bypasses the DAO fakes. */
    private suspend fun direct(
        creators: Set<String> = emptySet(),
        tags: Set<String> = emptySet(),
        keywords: List<String> = emptyList(),
    ): ContentFilter =
        ContentFilter().apply {
            refreshFrom(
                blockedCreators = creators,
                blockedTags = tags,
                blockedKeywords = keywords,
            )
        }

    @Test
    fun `verifiedOnly wins over every other hide reason`() =
        runTest {
            val f = direct(creators = setOf("x"), tags = setOf("bigger"), keywords = listOf("fem"))
            // unverified gif matching EVERY other rule still reports unverified first
            assertEquals("unverified", f.hideReason("x", gifTags, "fem boy", gifVerified = false, verifiedOnly = true))
            // a verified gif clears verifiedOnly and falls through to the prefs
            assertEquals("creator", f.hideReason("x", gifTags, "fem boy", gifVerified = true, verifiedOnly = true))
            assertNull(f.hideReason("y", listOf("solo"), "clean", gifVerified = true, verifiedOnly = true))
        }

    @Test
    fun `prefs tag wins as reason when a tag is in both the prefs AND a blocked feed`() =
        runTest {
            val f = ContentFilter()
            f.refreshFrom(FakeContentPrefsDao().apply { upsertTag(TagPrefEntity("femboy", "BLOCKED", 0)) })
            f.refreshBlockedFeeds(
                object : CustomFeedDao {
                    override fun all(): kotlinx.coroutines.flow.Flow<List<CustomFeedEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())

                    override suspend fun byId(id: Long): CustomFeedEntity? = null

                    override suspend fun upsert(feed: CustomFeedEntity): Long = 0

                    override suspend fun delete(id: Long) {}

                    override suspend fun blocked(): List<CustomFeedEntity> =
                        listOf(CustomFeedEntity(1, "No Femboy", "femboy,tag:spam", 0, "BLOCKED"))
                },
            )
            // the same tag blocked both ways reports the prefs reason first
            assertEquals("tag", f.hideReason("anyone", gifTags))
            // a feed-only tag reports "feed"
            assertEquals("feed", f.hideReason("anyone", listOf("spam")))
        }

    @Test
    fun `refreshBlockedFeeds merges creators into refreshFrom's set but a later refreshFrom replaces it`() =
        runTest {
            val f = ContentFilter()
            f.refreshFrom(FakeContentPrefsDao().apply { upsertCreator(CreatorPrefEntity("alpha", "BLOCKED", 0)) })
            f.refreshBlockedFeeds(
                object : CustomFeedDao {
                    override fun all(): kotlinx.coroutines.flow.Flow<List<CustomFeedEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())

                    override suspend fun byId(id: Long): CustomFeedEntity? = null

                    override suspend fun upsert(feed: CustomFeedEntity): Long = 0

                    override suspend fun delete(id: Long) {}

                    override suspend fun blocked(): List<CustomFeedEntity> =
                        listOf(CustomFeedEntity(1, "No Beta", "creator:beta", 0, "BLOCKED"))
                },
            )
            assertEquals("creator", f.hideReason("beta", gifTags))
            assertEquals("creator", f.hideReason("alpha", gifTags))
            // document the Stage-2 ordering constraint from the source: a later
            // refreshFrom REPLACES the creators set, so the blocked-feed ref
            // disappears until refreshBlockedFeeds re-runs after it
            f.refreshFrom(emptySet(), emptySet(), emptyList())
            assertNull(f.hideReason("beta", gifTags))
            assertNull(f.hideReason("alpha", gifTags))
        }

    @Test
    fun `refreshBlockedFeeds replaces the whole feed set`() =
        runTest {
            val f = ContentFilter()
            f.refreshBlockedFeeds(
                object : CustomFeedDao {
                    override fun all(): kotlinx.coroutines.flow.Flow<List<CustomFeedEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())

                    override suspend fun byId(id: Long): CustomFeedEntity? = null

                    override suspend fun upsert(feed: CustomFeedEntity): Long = 0

                    override suspend fun delete(id: Long) {}

                    override suspend fun blocked(): List<CustomFeedEntity> = listOf(CustomFeedEntity(1, "A", "tag:spam", 0, "BLOCKED"))
                },
            )
            assertEquals("feed", f.hideReason("anyone", listOf("spam")))
            // dao now unblocks feed 1 (its empty) → "spam" no longer hidden
            f.refreshBlockedFeeds(
                object : CustomFeedDao {
                    override fun all(): kotlinx.coroutines.flow.Flow<List<CustomFeedEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())

                    override suspend fun byId(id: Long): CustomFeedEntity? = null

                    override suspend fun upsert(feed: CustomFeedEntity): Long = 0

                    override suspend fun delete(id: Long) {}

                    override suspend fun blocked(): List<CustomFeedEntity> = emptyList()
                },
            )
            assertNull(f.hideReason("anyone", listOf("spam")))
        }

    @Test
    fun `keyword matching is literal, not regex`() =
        runTest {
            val f = direct(keywords = listOf("a.c"))
            // "." must NOT act as a wildcard
            assertNull(f.hideReason("anyone", listOf("abcXdef")))
            assertEquals("keyword", f.hideReason("anyone", listOf("a.c")))
            // other metacharacters too
            val star = direct(keywords = listOf("*"))
            assertNull(star.hideReason("anyone", listOf("abc")))
        }

    @Test
    fun `any keyword in the list trips the block, tags first`() =
        runTest {
            val f = direct(keywords = listOf("cat", "dog"))
            assertEquals("keyword", f.hideReason("anyone", listOf("hotdog")))
            assertEquals("keyword", f.hideReason("anyone", emptyList(), "it was a CAT"))
            assertNull(f.hideReason("anyone", listOf("mouse")))
        }

    @Test
    fun `keyword matches description only when a description is supplied`() =
        runTest {
            val f = direct(keywords = listOf("balloons"))
            assertEquals("keyword", f.hideReason("anyone", listOf("solo"), "Beach balloons"))
            assertNull(f.hideReason("anyone", listOf("solo")))
            assertNull(f.hideReason("anyone", listOf("solo"), null))
        }

    @Test
    fun `hideReason is case-insensitive on username and tags`() =
        runTest {
            val f = direct(creators = setOf("DARTH"), tags = setOf("GoBlin"))
            assertEquals("creator", f.hideReason("dArTh", emptyList()))
            assertEquals("tag", f.hideReason("anyone", listOf("GOBLIN")))
            // feed tags were lowercased on refresh too
            assertEquals("tag", f.hideReason("anyone", listOf("goblin")))
        }

    @Test
    fun `leak-zero contract — allow exactly mirrors hideReason for every combo`() =
        runTest {
            val f =
                direct(
                    creators = setOf("x"),
                    tags = setOf("bigger"),
                    keywords = listOf("fem"),
                )
            val creators = listOf("x", "y")
            val tagSets = listOf(gifTags, listOf("solo"), emptyList())
            val descriptions = listOf(null, "", "fem boy", "clean")
            val verifiedOnlyFlags = listOf(false, true)
            val verifiedFlags = listOf(false, true)
            for (c in creators) {
                for (tags in tagSets) {
                    for (d in descriptions) {
                        for (vo in verifiedOnlyFlags) {
                            for (v in verifiedFlags) {
                                // keep the argument name explicit: a gif with a
                                // non-null reason must never render (allow == false)
                                val reason = f.hideReason(c, tags, d, v, vo)
                                assertEquals(
                                    "allow mismatch for reason=$reason c=$c tags=$tags d=$d v=$v vo=$vo",
                                    reason == null,
                                    f.allow(c, tags, d, v, vo),
                                )
                            }
                        }
                    }
                }
            }
        }

    @Test
    fun `empty gifTags and empty inputs never block on their own`() =
        runTest {
            val f = direct()
            assertNull(f.hideReason("", emptyList(), null))
            assertTrue(f.allow("x", emptyList(), ""))
            assertTrue(f.allow("", emptyList(), ""))
        }
}
