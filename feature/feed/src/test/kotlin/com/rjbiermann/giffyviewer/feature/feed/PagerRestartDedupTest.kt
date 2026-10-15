package com.rjbiermann.giffyviewer.feature.feed

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #26: a DataStore write re-emits the WHOLE Preferences map on ANY key
 * change — FeedRepository.paging's combine→flatMapLatest pager-restart chain
 * tore down and rebuilt every live pager on unrelated writes (user report:
 * mute toggling in the player "restarted/refreshed the feed"; also burned
 * network page-1 requests for Liked/ForYou). The fix: one
 * `distinctUntilChanged()` between each combine and flatMapLatest (the same
 * guard FeedViewModel's pref invalidation already used). Dedupe must NOT eat
 * real filter moves — the Triple/Pair values differ for a true change.
 *
 * This test pins the chain shape at the repository's combine exit: an
 * identity re-emission (the mute-write pulse) does not restart the "pager";
 * a real value change does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PagerRestartDedupTest {
    @Test
    fun `identity re-emission does not restart pager - real change does`() =
        runTest {
            var restarts = 0
            val verifiedOnly = MutableStateFlow(false)
            // The prefs-like upstream: identity re-emission = the DataStore pulse
            // (same values, fresh emission), as any unrelated key write produces.
            val orientation =
                flow {
                    emit("any")
                    delay(10)
                    emit("any")
                    delay(10)
                    emit("horizontal")
                }
            val job =
                launch {
                    combine(orientation, verifiedOnly) { o, v -> o to v }
                        .distinctUntilChanged()
                        .flatMapLatest { (o, v) ->
                            // Pager-factory stand-in: one pass-through = one restart.
                            restarts += 1
                            flowOf(o to v)
                        }.collect {}
                }
            advanceUntilIdle()
            job.cancel()
            // Initial + the REAL filter change; the identity pulse suppressed.
            assertEquals(2, restarts)
        }
}
