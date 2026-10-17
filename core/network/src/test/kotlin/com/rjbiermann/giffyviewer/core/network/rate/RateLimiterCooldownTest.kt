package com.rjbiermann.giffyviewer.core.network.rate

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cooldown-extension window math for the plan-§4 limiter: 429 Retry-After
 * clamps (min 5s, max retryAfter+30s), `maxOf` extension semantics (a later
 * shorter cooldown never shortens the current one), and the interplay between
 * cooldown wait and the rolling-window budget (the 15/5s invariant holds with
 * a cooldown interleaved).
 */
class RateLimiterCooldownTest {
    @Test
    fun `later shorter cooldown never shortens the current one`() =
        runTest {
            var virtualNow = 0L
            val limiter =
                RollingWindowRateLimiter(now = { virtualNow }, delayFn = { virtualNow += it })

            limiter.startCooldown(retryAfterMs = 60_000, nowMs = 0) // until 60_000
            limiter.startCooldown(retryAfterMs = 1_000, nowMs = 2_000) // clamped 5s → 7_000 < 60_000
            limiter.acquireSlot()
            assertEquals("cooldown must still wait out the first 429", 60_000, virtualNow)
        }

    @Test
    fun `later longer cooldown extends via maxOf`() =
        runTest {
            var virtualNow = 0L
            val limiter =
                RollingWindowRateLimiter(now = { virtualNow }, delayFn = { virtualNow += it })

            limiter.startCooldown(retryAfterMs = 5_000, nowMs = 0) // until 5_000
            limiter.startCooldown(retryAfterMs = 30_000, nowMs = 1_000) // until 31_000
            limiter.acquireSlot()
            assertEquals(31_000, virtualNow)
        }

    @Test
    fun `cooldown wait also satisfies the window after it ends`() =
        runTest {
            var virtualNow = 0L
            val limiter =
                RollingWindowRateLimiter(
                    maxPerWindow = 2,
                    now = { virtualNow },
                    delayFn = { virtualNow += it },
                )

            limiter.acquireSlot()
            limiter.acquireSlot() // burst of 2, window full
            limiter.startCooldown(retryAfterMs = 5_000, nowMs = 0)
            // the cooldown wait lands at t=5000, where both window entries have
            // expired → the slot is granted immediately after, no double wait
            limiter.acquireSlot()
            assertEquals(5_000, virtualNow)
        }

    @Test
    fun `cooldown and full window stack serially`() =
        runTest {
            var virtualNow = 0L
            val limiter =
                RollingWindowRateLimiter(
                    maxPerWindow = 2,
                    now = { virtualNow },
                    delayFn = { virtualNow += it },
                )

            limiter.acquireSlot()
            limiter.acquireSlot()
            limiter.startCooldown(retryAfterMs = 10_000, nowMs = 0)
            // window entries get pruned at t=10000 (age ≥ 5000) → granted there
            limiter.acquireSlot()
            assertEquals(10_000, virtualNow)
            // window restarts fresh after the cooldown: short burst is free
            limiter.acquireSlot()
            assertEquals(10_000, virtualNow)
        }

    @Test
    fun `15-per-5s invariant holds with a cooldown interleaved`() =
        runTest {
            var virtualNow = 0L
            val limiter =
                RollingWindowRateLimiter(maxPerWindow = 15, now = { virtualNow }, delayFn = { virtualNow += it })

            val grantedAt = mutableListOf<Long>()
            limiter.startCooldown(retryAfterMs = 4_000, nowMs = 0)
            repeat(20) {
                limiter.acquireSlot()
                grantedAt.add(virtualNow)
            }
            for (i in grantedAt.indices) {
                val inWindow = grantedAt.count { it in grantedAt[i] until grantedAt[i] + 5_000 }
                assertTrue("window at grant $i has $inWindow entries", inWindow <= 15)
            }
            assertTrue("cooldown wait must precede any grant", grantedAt.first() >= 5_000)
        }

    @Test
    fun `cooldown at a virtual future time still waits from the later now`() =
        runTest {
            var virtualNow = 0L
            val limiter =
                RollingWindowRateLimiter(maxPerWindow = 15, now = { virtualNow }, delayFn = { virtualNow += it })

            limiter.startCooldown(retryAfterMs = 60_000, nowMs = 50_000) // until 110_000
            limiter.acquireSlot()
            assertEquals("grant waits until cooldownUntil from t=1000", 110_000, virtualNow)
        }
}
