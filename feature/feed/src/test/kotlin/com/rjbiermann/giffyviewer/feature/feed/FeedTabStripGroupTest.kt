package com.rjbiermann.giffyviewer.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #25 Feeds group rules (pure strip logic — no Compose): group membership,
 * the Feeds-chip tap target, sub-row visibility, and exact sub selection.
 */
class FeedTabStripGroupTest {
    /** #25 committed membership, order-sensitive: Trending · week · month · all-time. */
    @Test
    fun `feedGroup_def exact membership in order`() {
        assertEquals(
            listOf(
                FeedSource.Trending,
                FeedSource.TopThisWeek,
                FeedSource.TopRanged("top28"),
                FeedSource.TopRanged("top"),
            ),
            FEED_GROUP,
        )
    }

    @Test
    fun `feedGroup_def excludes non-group sources`() {
        assertFalse(FeedSource.TopRanged("top7") in FEED_GROUP)
        assertFalse(FeedSource.ForYou in FEED_GROUP)
        assertFalse(FeedSource.Favorites in FEED_GROUP)
        assertFalse(FeedSource.Liked in FEED_GROUP)
    }

    @Test
    fun `tapTarget_insideGroup_returnsCurrent`() {
        assertEquals(FeedSource.Trending, feedGroupTapTarget(FeedSource.Trending, FeedSource.TopRanged("top")))
        assertEquals(
            FeedSource.TopRanged("top"),
            feedGroupTapTarget(FeedSource.TopRanged("top"), FeedSource.Trending),
        )
    }

    @Test
    fun `tapTarget_outsideGroup_returnsLastSub default Trending`() {
        assertEquals(
            FeedSource.TopRanged("top28"),
            feedGroupTapTarget(FeedSource.ForYou, FeedSource.TopRanged("top28")),
        )
        assertEquals(
            FeedSource.Trending,
            feedGroupTapTarget(FeedSource.Creator("someone"), FeedSource.Trending),
        )
    }

    /** Sub-row visibility predicate: visible iff a group member is active. */
    @Test
    fun `subRowVisibility_visible_iff_groupMember`() {
        assertFalse(isInFeedGroup(FeedSource.ForYou))
        assertFalse(isInFeedGroup(FeedSource.Favorites))
        assertFalse(isInFeedGroup(FeedSource.Liked))
        assertTrue(isInFeedGroup(FeedSource.Trending))
        assertTrue(isInFeedGroup(FeedSource.TopThisWeek))
        assertTrue(isInFeedGroup(FeedSource.TopRanged("top28")))
        assertTrue(isInFeedGroup(FeedSource.TopRanged("top")))
    }

    /** Sub selection is exact FeedSource equality: month and all-time differ. */
    @Test
    fun `selectedSub_exactMatch top28_not_top`() {
        assertNotEquals(FeedSource.TopRanged("top28"), FeedSource.TopRanged("top"))
        assertEquals(FeedSource.TopThisWeek, FeedSource.TopThisWeek)
    }
}
