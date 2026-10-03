package com.rjbiermann.giffyviewer.core.network.rate

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingWindowRateLimiterTest {
    @Test
    fun `never more than 15 requests in any rolling 5-second window`() =
        runTest {
            var virtualNow = 0L
            val limiter = RollingWindowRateLimiter(now = { virtualNow }, delayFn = { virtualNow += it })
            val grantedAt = mutableListOf<Long>()

            repeat(45) {
                limiter.acquireSlot()
                grantedAt.add(virtualNow)
            }

            // invariant: any window of 5_000ms contains at most 15 grants
            for (i in grantedAt.indices) {
                val inWindow = grantedAt.count { it in grantedAt[i] until grantedAt[i] + 5_000 }
                assertTrue("window starting at grant $i has $inWindow entries", inWindow <= 15)
            }
        }

    @Test
    fun `burst of 15 passes immediately, 16th waits`() =
        runTest {
            var virtualNow = 0L
            val limiter = RollingWindowRateLimiter(now = { virtualNow }, delayFn = { virtualNow += it })

            repeat(15) { limiter.acquireSlot() }
            assertEquals(0, virtualNow) // burst costs zero elapsed time
            limiter.acquireSlot()
            assertEquals(5_000, virtualNow) // 16th waits for the window to slide
        }

    @Test
    fun `sustained rate is 3 per second`() =
        runTest {
            var virtualNow = 0L
            val limiter = RollingWindowRateLimiter(now = { virtualNow }, delayFn = { virtualNow += it })

            repeat(15) { limiter.acquireSlot() } // burst
            val t0 = virtualNow
            repeat(20) { limiter.acquireSlot() } // sustained phase
            val elapsed = virtualNow - t0
            // 20 requests after a full burst: all 15 slots share start time 0, so the
            // whole window slides at once — 20 more arrive in two windows → ~10s
            assertTrue("sustained 20 reqs took ${elapsed}ms", elapsed in 9_500..11_000)
        }

    @Test
    fun `cooldown from 429 is clamped to min 5s`() =
        runTest {
            var virtualNow = 0L
            val limiter = RollingWindowRateLimiter(now = { virtualNow }, delayFn = { virtualNow += it })

            limiter.startCooldown(retryAfterMs = 100, nowMs = 0)
            limiter.acquireSlot()
            assertTrue(virtualNow >= 5_000)
        }

    @Test
    fun `cooldown honors retryAfter within the cap`() =
        runTest {
            var virtualNow = 0L
            val limiter = RollingWindowRateLimiter(now = { virtualNow }, delayFn = { virtualNow += it })

            // 60s Retry-After is under the retryAfter+30s cap → waits 60s exactly
            limiter.startCooldown(retryAfterMs = 60_000, nowMs = 0)
            limiter.acquireSlot()
            assertEquals(60_000, virtualNow)
        }
}
