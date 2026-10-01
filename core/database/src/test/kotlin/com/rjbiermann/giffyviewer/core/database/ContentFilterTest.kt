package com.rjbiermann.giffyviewer.core.database

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Leak-zero invariant checks for the ContentFilter pipeline (PLAN §6). */
class ContentFilterTest {
    private val gifTags = listOf("Bigger", "Femboy", "NSFW")

    @Test
    fun `blocked niche-group tags feed the pipeline (stage 2)`() =
        runTest {
            val f = ContentFilter()
            f.refreshFrom(FakeContentPrefsDao())
            f.refreshGroupTags(
                object : NicheGroupDao {
                    override suspend fun upsert(group: NicheGroupEntity): Long = 0

                    override fun all(): kotlinx.coroutines.flow.Flow<List<NicheGroupEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())

                    override suspend fun blocked(): List<NicheGroupEntity> =
                        listOf(NicheGroupEntity(1, "No Femboy", " bigger ,femboy,, ", "BLOCKED", 0))

                    override suspend fun delete(id: Long) {}
                },
            )
            assertEquals("group", f.hideReason("anyone", gifTags))
            // whitespace/empty tag entries are ignored, not treated as tags
            assertNull(f.hideReason("anyone", listOf("Solo")))
        }

    private suspend fun filter(
        creators: Set<String> = emptySet(),
        tags: Set<String> = emptySet(),
        keywords: List<String> = emptyList(),
    ): ContentFilter =
        ContentFilter().apply {
            refreshFrom(
                FakeContentPrefsDao().apply {
                    creators.forEach { upsertCreator(CreatorPrefEntity(it, "BLOCKED", 0)) }
                    tags.forEach { upsertTag(TagPrefEntity(it, "BLOCKED", 0)) }
                    keywords.forEach { blockKeyword(KeywordBlockEntity(it, 0)) }
                },
            )
        }

    @Test
    fun `allows unblocked content`() =
        runTest {
            val f = filter()
            assertNull(f.hideReason("solarhelen", gifTags))
            assertTrue(f.allow("solarhelen", gifTags))
        }

    @Test
    fun `creator block is case-insensitive exact match`() =
        runTest {
            val f = filter(creators = setOf("SolarHelen"))
            assertEquals("creator", f.hideReason("solarhelen", gifTags))
            assertFalse(f.allow("solarhelen", gifTags))
        }

    @Test
    fun `tag block is exact match only`() =
        runTest {
            val f = filter(tags = setOf("femboy"))
            assertEquals("tag", f.hideReason("anyone", gifTags))
            // substring of a tag must NOT trip the tag rule
            assertNull(f.hideReason("anyone", listOf("femboys")))
        }

    @Test
    fun `keyword block is case-insensitive substring over tags`() =
        runTest {
            val f = filter(keywords = listOf("fem"))
            assertEquals("keyword", f.hideReason("anyone", gifTags))
        }

    @Test
    fun `creator wins over tag wins over keyword`() =
        runTest {
            val f = filter(creators = setOf("x"), tags = setOf("bigger"), keywords = listOf("big"))
            assertEquals("creator", f.hideReason("x", gifTags))
            assertEquals("tag", f.hideReason("y", gifTags))
        }

    @Test
    fun `refresh reloads from dao`() =
        runTest {
            val f = filter()
            assertNull(f.hideReason("solarhelen", gifTags))
            val f2 = filter(creators = setOf("solarhelen"))
            assertEquals("creator", f2.hideReason("solarhelen", gifTags))
        }
}

class WeekStartTest {
    private val week = WEEK_MS

    @Test
    fun `weekStart truncates to the 7-day bucket`() {
        assertEquals(0L, weekStartMs(0L))
        // a timestamp mid-bucket rounds DOWN to the bucket start
        assertEquals(2 * week, weekStartMs(2 * week + 5_000L))
        // exactly on a boundary stays on that boundary
        assertEquals(3 * week, weekStartMs(3 * week))
    }

    @Test
    fun `timestamps in one bucket share one weekStart`() {
        val now = 1_791_000_000_000L
        assertEquals(weekStartMs(now), weekStartMs(now + week / 2))
    }
}
