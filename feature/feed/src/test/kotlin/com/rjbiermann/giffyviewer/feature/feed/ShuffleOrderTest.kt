package com.rjbiermann.giffyviewer.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** §8 shuffle: one deterministic global order per seed, stable across loads. */
class ShuffleOrderTest {
    private fun shuffled(
        ids: List<String>,
        seed: Long,
    ): List<String> =
        ids.sortedBy {
            it.hashCode().toLong() xor (seed * -7046029254386353131L)
        }

    @Test
    fun `seed gives stable order`() {
        val ids = (1..50).map { "gif$it" }
        assertEquals(shuffled(ids, 99L), shuffled(ids, 99L))
        assertEquals(shuffled(ids, 99L), shuffled(ids.reversed(), 99L))
    }

    @Test
    fun `new seed changes the order`() {
        val ids = (1..50).map { "gif$it" }
        // adjacent seeds MUST differ (an additive key sorts the same for all)
        for (s in 1L..49L) assertNotEquals(shuffled(ids, s), shuffled(ids, s + 1))
    }

    @Test
    fun `shuffled keeps the same members`() {
        val ids = (1..50).map { "gif$it" }
        assertEquals(ids.toSet(), shuffled(ids, 7L).toSet())
    }
}
