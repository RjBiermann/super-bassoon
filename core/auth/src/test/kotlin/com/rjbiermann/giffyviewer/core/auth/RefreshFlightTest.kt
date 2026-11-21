package com.rjbiermann.giffyviewer.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * 401 refresh storm (2026-11 review): the API and image clients share the auth
 * interceptor, so many worker threads hit 401 at once and each started its own
 * blocking grant. The guard must serialize them and let later callers reuse the
 * first caller's result.
 */
class RefreshFlightTest {
    @Test
    fun `an already replaced token needs no grant`() {
        val flight = RefreshFlight()
        val grants = AtomicInteger()
        val ok = flight.run("old", { "new" }, { grants.incrementAndGet() > 0 })
        assertTrue("another caller's refresh already succeeded", ok)
        assertEquals(0, grants.get())
    }

    @Test
    fun `concurrent 401s with the same stale token share one grant`() {
        val flight = RefreshFlight()
        val current = AtomicReference("old")
        val grants = AtomicInteger()
        val grantStarted = CountDownLatch(1)
        val first =
            thread {
                flight.run(
                    "old",
                    { current.get() },
                    {
                        grants.incrementAndGet()
                        grantStarted.countDown()
                        Thread.sleep(200)
                        current.set("new")
                        true
                    },
                )
            }
        assertTrue(grantStarted.await(2, TimeUnit.SECONDS))
        val second = flight.run("old", { current.get() }, { grants.incrementAndGet() > 0 })
        first.join(5_000)

        assertEquals("exactly one grant for two 401s", 1, grants.get())
        assertTrue(second)
    }

    @Test
    fun `no stale token still refreshes and a failed grant reports false`() {
        val flight = RefreshFlight()
        assertFalse("a dead refresh token must report failure", flight.run(null, { "old" }, { false }))
        assertTrue(flight.run(null, { "old" }, { true }))
    }
}
