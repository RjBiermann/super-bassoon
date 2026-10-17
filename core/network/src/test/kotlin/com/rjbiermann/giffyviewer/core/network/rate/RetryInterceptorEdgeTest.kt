package com.rjbiermann.giffyviewer.core.network.rate

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.ConnectionPool
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Edge cases on the plan-§4 retry surface that [RetryAndBreakerTest] does not
 * pin: non-retryable 4xx semantics, the IOException retry path, 429
 * Retry-After clamping as published on the bus, 429 never feeding the
 * breaker, and the circuit-open 503's Retry-After arithmetic.
 */
class RetryInterceptorEdgeTest {
    private lateinit var server: MockWebServer
    private lateinit var bus: RateLimitBus
    private lateinit var limiter: RollingWindowRateLimiter

    @Before
    fun setUp() {
        server = MockWebServer()
        val dispatcher = mockwebserver3.QueueDispatcher()
        dispatcher.setFailFast(resp(404, body = "{\"error\":\"queue empty\"}"))
        server.dispatcher = dispatcher
        server.start()
        bus = kotlinx.coroutines.flow.MutableSharedFlow(replay = 16, extraBufferCapacity = 16)
        limiter = RollingWindowRateLimiter(maxPerWindow = 100, windowMs = 5_000)
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

    /** Simulates connection failures (the IOException feedstock) without touching
     *  mockwebserver's socket API: the first [failTimes] attempts throw. */
    private class BlipInterceptor(
        private val failTimes: Int,
    ) : Interceptor {
        var attempts = 0

        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            attempts++
            if (attempts <= failTimes) throw IOException("simulated connection blip")
            return chain.proceed(chain.request())
        }
    }

    private fun client(
        breaker: CircuitBreaker = CircuitBreaker(),
        backoff: List<Long> = listOf(1, 1),
        retryLimiter: RollingWindowRateLimiter = limiter,
        blips: Int = 0,
    ): OkHttpClient {
        val blip = BlipInterceptor(blips)
        // blip sits INNERMOST so its IOException lands inside RetryInterceptor's
        // catch (a throw above the retry layer escapes straight to the caller)
        return OkHttpClient
            .Builder()
            .addInterceptor(RateLimitInterceptor(limiter))
            .addInterceptor(
                RetryInterceptor(
                    limiter = retryLimiter,
                    bus = bus,
                    breaker = breaker,
                    backoffMs = backoff,
                ),
            ).addInterceptor(blip)
            .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
            .build()
    }

    private fun get(): Request = Request.Builder().url(server.url("/v2/x")).build()

    /** Limiter sized so cooldown waits dominate the asserts, never window waits. */
    private fun bigLimiter(): RollingWindowRateLimiter = RollingWindowRateLimiter(maxPerWindow = 100)

    private fun coolingDown(): RateLimitEvent.CoolingDown = bus.replayCache.filterIsInstance<RateLimitEvent.CoolingDown>().last()

    @Test
    fun `plain 4xx returns as-is with no retry and no breaker failure`() {
        server.enqueue(resp(404))
        val breaker = CircuitBreaker()
        val response = client(breaker, backoff = emptyList()).newCall(get()).execute()
        assertEquals(404, response.code)
        assertEquals(1, server.requestCount) // NOT retried
        assertFalse("only 5xx/IOException feed the breaker", breaker.isOpen())
    }

    @Test
    fun `connection failure then success retries and recovers`() {
        server.enqueue(resp(200))
        val response = client(blips = 1).newCall(get()).execute()
        assertEquals(200, response.code)
        assertEquals(1, server.requestCount)
        assertTrue(bus.replayCache.last() is RateLimitEvent.Recovered)
    }

    @Test
    fun `connection failures feed the breaker and open it`() {
        val breaker = CircuitBreaker(failureThreshold = 3)
        // one client whose every attempt blips: three calls = three IOExceptions
        val c = client(breaker, backoff = emptyList(), blips = Int.MAX_VALUE)
        repeat(3) { runCatching { c.newCall(get()).execute() } }
        assertTrue("3 connection failures must open the circuit", breaker.isOpen())

        // open circuit → synthetic 503, never a throw; the blip never fires again
        val response = c.newCall(get()).execute()
        assertEquals(503, response.code)
        assertEquals("synthetic 503 never reaches the server", 0, server.requestCount)
    }

    @Test
    fun `429 with tiny Retry-After is clamped up to the 5s minimum on the bus`() {
        // single attempt (no backoff) — a retry would block the REAL cooldown out
        server.enqueue(resp(429, retryAfter = "1"))
        val result = runCatching { client(retryLimiter = bigLimiter(), backoff = emptyList()).newCall(get()).execute() }
        assertTrue(result.isFailure) // 429 handled then retries-exhausted IOException
        val until = coolingDown().untilEpochMs - System.currentTimeMillis()
        assertTrue("1s Retry-After must clamp to the 5s minimum: got ${until}ms", until in 4_900L..10_000L)
    }

    @Test
    fun `429 Retry-After within the cap is honored as published`() {
        server.enqueue(resp(429, retryAfter = "60"))
        val result = runCatching { client(retryLimiter = bigLimiter(), backoff = emptyList()).newCall(get()).execute() }
        assertTrue(result.isFailure)
        val until = coolingDown().untilEpochMs - System.currentTimeMillis()
        assertTrue("60s Retry-After under the cap must be honored as-is: got ${until}ms", until in 59_000L..65_000L)
    }

    @Test
    fun `429 unparseable Retry-After falls back to the 5s default`() {
        server.enqueue(resp(429, retryAfter = "not-a-number"))
        val result = runCatching { client(retryLimiter = bigLimiter(), backoff = emptyList()).newCall(get()).execute() }
        assertTrue(result.isFailure)
        val event = coolingDown()
        val until = event.untilEpochMs - System.currentTimeMillis()
        assertTrue("garbage Retry-After falls back to 5s: got ${until}ms", until in 4_900L..10_000L)
        assertTrue(event.reason.contains("429"))
    }

    @Test
    fun `429s never feed the circuit breaker`() {
        repeat(3) { server.enqueue(resp(429, retryAfter = "1")) }
        val breaker = CircuitBreaker(failureThreshold = 3)
        val result = runCatching { client(breaker, retryLimiter = bigLimiter()).newCall(get()).execute() }
        assertTrue("cooldown-only responses exhaust retries as an IOException", result.isFailure)
        assertFalse("429 is throttling, not a breaker failure", breaker.isOpen())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `circuit-open 503 carries a whole-second Retry-After and never hits the server`() {
        val breaker = CircuitBreaker(failureThreshold = 3, openDurationMs = 60_000)
        repeat(3) { breaker.recordFailure() }
        val response = client(breaker, backoff = emptyList()).newCall(get()).execute()
        assertEquals(503, response.code)
        val retryAfterS = response.header("Retry-After")?.toLongOrNull()
        assertTrue("Retry-After $retryAfterS must be ~60 seconds", retryAfterS in 59L..60L)
        assertEquals("synthetic 503 never reaches the server", 0, server.requestCount)
    }
}
