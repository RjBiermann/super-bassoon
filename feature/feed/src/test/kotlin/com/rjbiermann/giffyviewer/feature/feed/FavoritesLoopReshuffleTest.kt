package com.rjbiermann.giffyviewer.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #36 loop-reshuffle mapping (pure): loop 0 keeps the #5 refresh rotation
 * (rotateCreators with the volatile session seed); every later loop
 * re-shuffles the creator order with a NEW derived seed, stdlib-driven
 * (Collections.shuffle with a seeded java.util.Random — spec-stable and
 * deterministic on every JVM/Android runtime). The pinned shuffle outputs
 * below are the exact results of that algorithm over the pinned loopSeed
 * arithmetic constants; a runtime change would be a deliberate contract
 * change, not drift.
 */
class FavoritesLoopReshuffleTest {
    @Test
    fun `loop 0 keeps the refresh rotation contract`() {
        val creators = listOf("a", "b", "c", "d", "e")
        // Seed 11 ≡ 1/5 offset: rotateCreators([a..e], 11) = [b,c,d,e,a];
        // seed 2 ≡ 2/5 offset: [c,d,e,a,b] — the #5 tests pin the same rule.
        assertEquals(listOf("b", "c", "d", "e", "a"), favoritesLoopOrder(creators, 11L, 0))
        assertEquals(listOf("c", "d", "e", "a", "b"), favoritesLoopOrder(creators, 2L, 0))
    }

    @Test
    fun `every loop reshuffles with a new derived seed`() {
        // Pinned arithmetic (seed * OP1 + loop * OP2 mod 2^64, both odd): a
        // fixed session seed maps each loop to a DISTINCT seed, so consecutive
        // loops never reshuffle with the same key.
        assertEquals(-3719345503411677977L, loopSeed(11L, 0))
        assertEquals(-3768410282401406540L, loopSeed(11L, 1))
        assertEquals(-3817475061391135103L, loopSeed(11L, 2))
        assertEquals(-3866539840380863666L, loopSeed(11L, 3))
        val seeds = (0L..8L).map { loopSeed(11L, it.toInt()) }
        assertEquals(seeds.size, seeds.toSet().size)
        for (loop in 1..2) {
            assertTrue(loopSeed(11L, loop) != 11L && loopSeed(11L, loop) != loopSeed(11L, loop - 1))
        }
    }

    @Test
    fun `later loops serve a different creator order and replay deterministically`() {
        val creators = listOf("a", "b", "c", "d", "e")
        // Exact stdlib-shuffle outputs over the pinned seeds above — the loop
        // boundary really does change the served grouping order:
        assertEquals(listOf("e", "a", "c", "d", "b"), favoritesLoopOrder(creators, 11L, 1))
        assertEquals(listOf("c", "a", "e", "d", "b"), favoritesLoopOrder(creators, 11L, 2))
        assertEquals(listOf("d", "a", "c", "e", "b"), favoritesLoopOrder(creators, 11L, 3))
        // Deterministic within a session: same (creators, seed, loop) always
        // yields the same order — the page→creator mapping stays stable so the
        // cache rows and the DB-row dedup line up.
        assertEquals(favoritesLoopOrder(creators, 11L, 1), favoritesLoopOrder(creators, 11L, 1))
        // Order, not membership: each loop's order is a permutation of the pool.
        for (loop in 1..3) {
            assertEquals(creators.sorted(), favoritesLoopOrder(creators, 11L, loop).sorted())
        }
    }

    @Test
    fun `degenerate pools keep one trivial order at any loop`() {
        assertEquals(listOf("a"), favoritesLoopOrder(listOf("a"), 5L, 3))
        assertEquals(emptyList<String>(), favoritesLoopOrder(emptyList(), 5L, 2))
        assertEquals(listOf("a"), favoritesLoopOrder(listOf("a"), 0L, 1))
    }
}
