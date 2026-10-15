package com.rjbiermann.giffyviewer.feature.settings

import com.rjbiermann.giffyviewer.core.database.CustomFeedDao
import com.rjbiermann.giffyviewer.core.database.CustomFeedEntity
import com.rjbiermann.giffyviewer.core.database.setFeedState
import com.rjbiermann.giffyviewer.feature.feed.nextCycleState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #17 slice: the Blocked panel's feed inventory — the BLOCKED filter
 *  plus the shared merged-groups cycle write (one owner:
 *  [setFeedState] + [nextCycleState], delegated to by CustomFeedsScreen). */
class SettingsViewModelTest {
    private class RecordingFeedDao : CustomFeedDao {
        val upserted = mutableListOf<CustomFeedEntity>()

        override fun all(): Flow<List<CustomFeedEntity>> = MutableStateFlow(emptyList())

        override suspend fun byId(id: Long): CustomFeedEntity? = null

        override suspend fun upsert(feed: CustomFeedEntity): Long {
            upserted += feed
            return feed.id
        }

        override suspend fun delete(id: Long) {}

        override suspend fun blocked(): List<CustomFeedEntity> = upserted.filter { it.state == "BLOCKED" }
    }

    @Test
    fun `blocked feed inventory is exactly the BLOCKED subset`() {
        val feeds =
            listOf(
                CustomFeedEntity(1, "Tabbed", "tag:art", 0, "FAVORITED"),
                CustomFeedEntity(2, "Hidden", "tag:inflation", 0, "BLOCKED"),
                CustomFeedEntity(3, "Open only", "tag:art", 0, "NEUTRAL"),
            )
        assertEquals(listOf(CustomFeedEntity(2, "Hidden", "tag:inflation", 0, "BLOCKED")), blockedFeedRows(feeds))
    }

    @Test
    fun `shared cycle keeps its order and the settings unblock writes NEUTRAL`() =
        runTest {
            // The cycle mapping CustomFeedsScreen's button drives (unchanged).
            val favorited = CustomFeedEntity(1, "Tabbed", "tag:art", 0, "FAVORITED")
            assertEquals("BLOCKED", favorited.nextCycleState())
            assertEquals("FAVORITED", favorited.copy(state = "NEUTRAL").nextCycleState())
            // The settings "Unblock" path: from BLOCKED the same cycle's next
            // state is NEUTRAL, written once via the shared DAO extension.
            val dao = RecordingFeedDao()
            val blocked = CustomFeedEntity(7, "Hidden", "tag:inflation", 0, "BLOCKED")
            dao.setFeedState(blocked, blocked.nextCycleState())
            assertEquals(listOf(blocked.copy(state = "NEUTRAL")), dao.upserted)
        }
}
