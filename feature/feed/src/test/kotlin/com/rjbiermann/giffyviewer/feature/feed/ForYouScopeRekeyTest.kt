package com.rjbiermann.giffyviewer.feature.feed

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #38 (2026-10 e2e: the For You scope chips — All/Creators/Niches — did
 * nothing, focus stayed on the chip). Two contracts pinned at the repository
 * choke point:
 * 1. `forYouPagingKeys`: the pager generation re-keys on ALL three settings
 *    (orientation, verified-only, scope) — a scope change maps to a NEW key
 *    so both shells' rows refetch; unrelated DataStore identity re-emissions
 *    collapse (dedupe on the combine EXIT).
 * 2. `isForYouMemoFresh`: the 5-min ForYou-context memo only serves for the
 *    SAME scope within its TTL — the re-key's refetch reads freshly fetched
 *    follows, never a stale-scope memo; resetting/reverting the scope within
 *    the TTL still reuses the memo (per-page loads stay rate-limit cheap).
 */
class ForYouScopeRekeyTest {
    @Test
    fun `scope change re-keys the pager and DataStore identity re-emissions collapse`() =
        runTest {
            // MutableSharedFlow (no conflation): identical replayed values must
            // be deduped by forYouPagingKeys itself, not by the source.
            val orientation = MutableSharedFlow<String>(extraBufferCapacity = 8)
            val verified = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
            val scope = MutableSharedFlow<String>(extraBufferCapacity = 8)
            val seen = mutableListOf<Triple<String, Boolean, String>>()
            val job =
                launch {
                    forYouPagingKeys(orientation, verified, scope).collect { seen.add(it) }
                }
            runCurrent()

            orientation.tryEmit("any")
            verified.tryEmit(false)
            scope.tryEmit("all")
            runCurrent()
            // The scope chip is chosen: new key → pager restart → refetch.
            scope.tryEmit("creators")
            runCurrent()
            // DataStore re-emits the WHOLE Preferences map on any write — the
            // identical state must NOT re-key (the #26 mute-flip lesson).
            scope.tryEmit("creators")
            verified.tryEmit(false)
            runCurrent()
            // The other two settings still re-key on their own.
            verified.tryEmit(true)
            runCurrent()
            orientation.tryEmit("horizontal")
            runCurrent()
            job.cancel()

            assertEquals(
                listOf(
                    Triple("any", false, "all"),
                    Triple("any", false, "creators"),
                    Triple("any", true, "creators"),
                    Triple("horizontal", true, "creators"),
                ),
                seen,
            )
        }

    @Test
    fun `the 5-min memo serves only the same scope within its ttl`() {
        val at = 1_000_000L
        val memo = FeedRepository.ForYouContext("creators", setOf("some.creator"), emptySet(), at)
        // Same scope, inside the TTL → served (per-page loads stay cheap).
        assertTrue(isForYouMemoFresh(memo, "creators", at + 30_000))
        // TTL lapse → refetch.
        assertTrue("boundary is exclusive", !isForYouMemoFresh(memo, "creators", at + FeedRepository.CTX_TTL_MS))
        // Scope switch invalidates immediately, even mid-TTL (the #38 chain).
        assertFalse(isForYouMemoFresh(memo, "all", at + 1_000))
        assertFalse(isForYouMemoFresh(memo, "niches", at + 1_000))
        // No memo written → fetch.
        assertFalse(isForYouMemoFresh(null, "creators", at))
    }
}
