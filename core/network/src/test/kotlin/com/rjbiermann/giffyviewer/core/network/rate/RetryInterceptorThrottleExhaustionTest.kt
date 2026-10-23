package com.rjbiermann.giffyviewer.core.network.rate

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Issue #63: an ALL-429 exhaustion is throttling, not a dead network — it ends at
 * `RetryInterceptor`'s exhaustion site with no exception of its own (429 sets no
 * `lastException`), so the UI could only ever render the offline copy. Pins the
 * dedicated [#RateLimitedException], its IOException compatibility (every existing
 * `catch (IOException)` site keeps working) and the #58 rule that 429s never feed
 * the circuit breaker.
 */
class RetryInterceptorThrottleExhaustionTest {
    private lateinit var server: MockWebServer
    private lateinit var bus: RateLimitBus
    private lateinit var limiter: RollingWindowRateLimiter

    // 429 cooldowns are honoured via a virtual clock so the test never sleeps
    // the real 5s minimum (same shape as RollingWindowRateLimiterTest).
    private var clock = 1_000L

    @Before
    fun setUp() {
        server = MockWebServer()
        val dispatcher = mockwebserver3.QueueDispatcher()
        dispatcher.setFailFast(resp(404, body = "{\"error\":\"queue empty\"}"))
        server.dispatcher = dispatcher
        server.start()
        bus = kotlinx.coroutines.flow.MutableSharedFlow(replay = 8, extraBufferCapacity = 16)
        limiter =
            RollingWindowRateLimiter(
                maxPerWindow = 100,
                windowMs = 5_000,
                now = { clock },
                delayFn = { clock += it },
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun resp(
        code: Int,
        body: String = "{}",
        retryAfter: String? = null,
    ): MockResponse =
        MockResponse
            .Builder()
            .code(code)
            .body(body)
            .apply { retryAfter?.let { headers(Headers.headersOf("Retry-After", it)) } }
            .build()

    private fun client(breaker: CircuitBreaker = CircuitBreaker()): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(RateLimitInterceptor(limiter))
            .addInterceptor(
                RetryInterceptor(
                    limiter = limiter,
                    bus = bus,
                    breaker = breaker,
                    backoffMs = listOf(1, 1), // attempts = 3
                ),
            ).build()

    private fun get(): Request = Request.Builder().url(server.url("/v2/x")).build()

    private fun all429Exhaustion(
        breaker: CircuitBreaker = CircuitBreaker(),
        retryAfter: String? = null,
    ): Throwable? {
        repeat(3) { server.enqueue(resp(429, retryAfter = retryAfter)) } // 1 initial + 2 retries
        return runCatching { client(breaker).newCall(get()).execute() }.exceptionOrNull()
    }

    @Test
    fun `429 then success returns the successful response`() {
        server.enqueue(resp(429))
        server.enqueue(resp(200))
        val response = client().newCall(get()).execute()
        assertEquals("throttling that recovers must not surface as an error", 200, response.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `no Retry-After header exhausts with the 5s default cooldown`() {
        val error = all429Exhaustion()
        assertTrue("expected RateLimitedException, got $error", error is RateLimitedException)
        assertEquals("429 without Retry-After uses the 5s default", 5_000L, (error as RateLimitedException).retryAfterMs)
        assertEquals(3, server.requestCount) // every attempt was really made
    }

    @Test
    fun `a 12s Retry-After is reported as 12000ms`() {
        val error = all429Exhaustion(retryAfter = "12")
        assertEquals(12_000L, (error as RateLimitedException).retryAfterMs)
    }

    @Test
    fun `a real failure plus throttling throws the plain IOException and counts one breaker failure`() {
        val breaker = CircuitBreaker(failureThreshold = 2)

        fun exhaust() {
            server.enqueue(resp(500))
            server.enqueue(resp(429))
            server.enqueue(resp(429))
            val error = runCatching { client(breaker).newCall(get()).execute() }.exceptionOrNull()
            assertTrue(
                "a real failure wins over the throttling: got $error",
                error is IOException && error !is RateLimitedException,
            )
        }
        exhaust()
        assertFalse("one exhausted call = one breaker failure (threshold 2 not reached)", breaker.isOpen())
        exhaust()
        assertTrue("two exhausted calls = two consecutive breaker failures", breaker.isOpen())
    }

    @Test
    fun `RateLimitedException is still an IOException so existing catch sites are unchanged`() {
        val error = all429Exhaustion()
        assertTrue("catch (IOException) must keep catching throttling", error is IOException)
    }

    @Test
    fun `429 exhaustion never feeds the circuit breaker`() {
        val breaker = CircuitBreaker(failureThreshold = 3, openDurationMs = 60_000)
        // 3 exhausted CALLS = 3 breaker failures only if throttling were a failure (#58).
        repeat(3) { all429Exhaustion(breaker) }
        assertFalse("429 is throttling, not a failure", breaker.isOpen())
    }
}
