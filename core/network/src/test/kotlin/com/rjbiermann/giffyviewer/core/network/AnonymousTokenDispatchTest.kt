package com.rjbiermann.giffyviewer.core.network

import com.rjbiermann.giffyviewer.core.network.rate.RateLimitInterceptor
import com.rjbiermann.giffyviewer.core.network.rate.RetryInterceptor
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * #64: the anonymous temp-token fetch must never wait for a dispatcher slot of the
 * client whose calls are waiting for that token. 5 concurrent feed calls saturate
 * OkHttp's `maxRequestsPerHost` (5), so a token call queued on the SAME client is
 * never dispatched, the `AnonymousSession` mutex is never released, and every row
 * stays in `LoadState.Loading` forever. The token client's own dispatcher breaks
 * the cycle; [AnonymousSession]'s acquisition bound turns a stuck fetch into a
 * fail-open (no Bearer) instead of a hang.
 *
 * `feedErrorKind` lives in `:feature:feed` and is not visible from `:core:network`,
 * so the offline-honesty pin here is the exception type: the bounded fetch fails as
 * an `IOException` (`InterruptedIOException` from the token client's callTimeout —
 * the shape `feedErrorKind` maps to `Offline`), never as a cancellation or a hang.
 */
class AnonymousTokenDispatchTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun ok(
        body: String = "{}",
        delayMs: Long = 0,
    ): MockResponse.Builder =
        MockResponse
            .Builder()
            .code(200)
            .body(body)
            .apply { if (delayMs > 0) bodyDelay(delayMs, TimeUnit.MILLISECONDS) }

    private fun stalled(): MockResponse.Builder = MockResponse.Builder().onResponseStart(SocketEffect.Stall)

    /** `/v2/auth/temporary` answered by [token]; everything else by [feed]. */
    private fun route(
        token: (RecordedRequest) -> MockResponse.Builder,
        feed: (RecordedRequest) -> MockResponse.Builder,
    ) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.url.encodedPath.endsWith("v2/auth/temporary")) {
                        token(request).build()
                    } else {
                        feed(request).build()
                    }
            }
    }

    private fun requests(): List<RecordedRequest> = (1..server.requestCount).mapNotNull { server.takeRequest(1, TimeUnit.SECONDS) }

    private fun RecordedRequest.isToken(): Boolean = url.encodedPath.endsWith("v2/auth/temporary")

    @Test
    fun `5 concurrent calls never deadlock on the anonymous token`() {
        // 300ms token delay so the four other callers are already parked on the mutex.
        route(token = { ok("""{"token":"temp"}""", delayMs = 300) }, feed = { ok() })
        // Same wiring shape as the app DI modules (the session needs the components).
        var session: AnonymousSession? = null
        val network = buildNetwork(baseUrl = server.url("/").toString(), authToken = { session?.token() })
        session = AnonymousSession(network.tokenApi)

        val latch = CountDownLatch(5)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val pool = Executors.newFixedThreadPool(5)
        repeat(5) {
            pool.execute {
                try {
                    runBlocking { network.api.trendingPopular() }
                } catch (t: Throwable) {
                    failures.add(t)
                } finally {
                    latch.countDown()
                }
            }
        }
        val finished = latch.await(3, TimeUnit.SECONDS)
        pool.shutdownNow()

        assertTrue("5 concurrent anonymous calls must not deadlock: $failures", finished)
        assertTrue("no call may fail: $failures", failures.isEmpty())

        val recorded = requests()
        val tokenRequests = recorded.filter { it.isToken() }
        val feedRequests = recorded.filter { !it.isToken() }
        assertEquals("the mutex single-flights the token fetch", 1, tokenRequests.size)
        assertEquals(5, feedRequests.size)
        assertTrue(
            "every feed request carries the shared token: ${feedRequests.map { it.headers["Authorization"] }}",
            feedRequests.all { it.headers["Authorization"] == "Bearer temp" },
        )
    }

    @Test
    fun `a token endpoint that never answers fails the acquisition instead of hanging`() =
        runBlocking {
            route(token = { stalled() }, feed = { ok() })
            // Default 10s token timeout: only the session's own bound can make this fast.
            val network = buildNetwork(baseUrl = server.url("/").toString())
            val session = AnonymousSession(network.tokenApi, acquireTimeoutMs = 300)

            val started = System.currentTimeMillis()
            val token = session.token()
            val elapsed = System.currentTimeMillis() - started

            assertNull("a bounded acquisition fails open", token)
            assertTrue("acquisition must be bounded, took ${elapsed}ms", elapsed < 2_000)

            // Fail open: the request still goes out (unauthenticated) instead of hanging,
            // and nothing leaks out of the acquisition as a cancellation.
            assertEquals(1, network.api.trendingPopular().page)
            val recorded = requests()
            assertNull(recorded.single { !it.isToken() }.headers["Authorization"])

            // The bounded fetch itself fails as an IOException, not a CancellationException.
            val fast = buildNetwork(baseUrl = server.url("/").toString(), tokenTimeoutMs = 300)
            val failure = runCatching { fast.tokenApi.temporaryToken() }.exceptionOrNull()
            assertTrue("expected an IOException, got $failure", failure is IOException)
        }

    @Test
    fun `the token request keeps the shared limiter and the browser identity`() {
        route(token = { ok("""{"token":"temp"}""") }, feed = { ok() })
        val network = buildNetwork(baseUrl = server.url("/").toString(), authToken = { "user.jwt" })

        // Isolation contract: sharing a Dispatcher reintroduces #64.
        assertNotSame(network.client.dispatcher, network.tokenClient.dispatcher)
        assertNotSame(network.imageClient.dispatcher, network.tokenClient.dispatcher)

        val tokenInterceptors = network.tokenClient.interceptors
        assertTrue(
            "the temp request is real API traffic and counts against ≤15/5s",
            tokenInterceptors.any { it is RateLimitInterceptor },
        )
        assertTrue("the token is UA-bound", tokenInterceptors.any { it is BrowserIdentityInterceptor })
        assertFalse("a failing token fetch must not back off", tokenInterceptors.any { it is RetryInterceptor })

        // No auth interceptor: the token fetch carries no Bearer even though authToken returns one.
        runBlocking { network.tokenApi.temporaryToken() }
        runBlocking { network.api.trendingPopular() }
        val recorded = requests()
        val tokenRequest = recorded.single { it.isToken() }
        val feedRequest = recorded.single { !it.isToken() }
        assertNull("no auth interceptor may ride the token client", tokenRequest.headers["Authorization"])
        assertEquals(feedRequest.headers["User-Agent"], tokenRequest.headers["User-Agent"])
        assertEquals(feedRequest.headers["X-Session-Id"], tokenRequest.headers["X-Session-Id"])
    }

    @Test
    fun `signed-in bearer and the 401 refresh path are unchanged`() {
        route(
            token = { ok("""{"token":"temp"}""") },
            feed = { request ->
                if (request.headers["Authorization"] == "Bearer user.jwt") {
                    MockResponse.Builder().code(401).body("""{"error":"TokenExpired"}""")
                } else {
                    ok()
                }
            },
        )
        val tokenReads = AtomicInteger(0)
        val unauthorized = AtomicInteger(0)
        val network =
            buildNetwork(
                baseUrl = server.url("/").toString(),
                authToken = { if (tokenReads.getAndIncrement() == 0) "user.jwt" else "new.jwt" },
                onUnauthorized = { unauthorized.incrementAndGet() },
            )

        runBlocking { network.api.trendingPopular() }

        assertEquals("one refresh, one retry", 1, unauthorized.get())
        val recorded = requests()
        assertTrue("signed-in traffic must never touch the token endpoint", recorded.none { it.isToken() })
        assertEquals(2, recorded.size)
        assertEquals("Bearer user.jwt", recorded[0].headers["Authorization"])
        assertEquals("Bearer new.jwt", recorded[1].headers["Authorization"])
    }
}
