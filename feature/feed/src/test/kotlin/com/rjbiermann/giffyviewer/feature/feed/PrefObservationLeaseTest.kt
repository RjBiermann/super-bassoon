package com.rjbiermann.giffyviewer.feature.feed

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rjbiermann.giffyviewer.core.model.Gif
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LEAK H3 (2026-10 OOM investigation) cleanup contract: every cached-pager
 * generation added one Room InvalidationTracker.Observer with the tracker as
 * a process-lifetime registry and ZERO removal call sites — each observer
 * pinned its whole FeedPagingSource. PrefObservationLease pairs the removal
 * with both ways a generation ends; the tracker's observer count must return
 * to baseline after every swap.
 */
class PrefObservationLeaseTest {
    private class RecordingTracker {
        val added = mutableListOf<androidx.room.InvalidationTracker.Observer>()
        val removed = mutableListOf<androidx.room.InvalidationTracker.Observer>()
    }

    private fun lease(
        tracker: RecordingTracker,
        tables: List<String> = listOf("creator_prefs"),
    ) = PrefObservationLease(
        tables = tables,
        addObserver = { tracker.added.add(it) },
        removeObserver = { tracker.removed.add(it) },
    )

    private fun bareSource(): PagingSource<Int, Gif> =
        object : PagingSource<Int, Gif>() {
            override suspend fun load(params: PagingSource.LoadParams<Int>): PagingSource.LoadResult<Int, Gif> =
                PagingSource.LoadResult.Page(emptyList(), null, null)

            override fun getRefreshKey(state: PagingState<Int, Gif>): Int? = null
        }

    @Test
    fun `generation dispose removes every observed source - count returns to baseline`() {
        val tracker = RecordingTracker()
        val lease = lease(tracker)
        repeat(3) { lease.observe(bareSource()) }
        assertEquals(3, tracker.added.size)
        assertTrue(tracker.removed.isEmpty())
        // The collector is cancelled (re-key / tab switch / process death):
        // the wrapper's finally runs dispose — net-zero observers afterwards.
        lease.dispose()
        assertEquals(tracker.added, tracker.removed)
        // Idempotent: a second release must not double-remove.
        lease.dispose()
        assertEquals(tracker.added, tracker.removed)
    }

    @Test
    fun `an invalidated source removes its own generation observer immediately`() {
        val tracker = RecordingTracker()
        val lease = lease(tracker)
        val source = bareSource()
        lease.observe(source)
        assertEquals(1, tracker.added.size)
        assertFalse(tracker.removed.contains(tracker.added[0]))
        // A pref-table write invalidates the source; the factory then builds
        // the NEXT source for the still-collecting flow, so the old observer
        // is removed inside the invalidated callback (never while the flow
        // keeps collecting its replacement).
        source.invalidate()
        assertEquals(listOf(tracker.added[0]), tracker.removed)
        // dispose after that removes nothing further (the entry left the lease).
        lease.dispose()
        assertEquals(listOf(tracker.added[0]), tracker.removed)
    }

    @Test
    fun `release carries an already-removed observer gracefully`() {
        val tracker = RecordingTracker()
        val lease = lease(tracker)
        val source = bareSource()
        lease.observe(source)
        source.invalidate()
        assertEquals(tracker.added, tracker.removed)
        // Removing an observer second time is a no-op (Room map.remove), so
        // both cleanup paths can race without throwing.
        lease.dispose()
        assertEquals(tracker.added.toList(), tracker.removed.toList())
    }
}
