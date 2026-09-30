package com.rjbiermann.giffyviewer.core.network.rate

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.ConnectionPool
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class RetryAndBreakerTest {
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
        bus = RateLimitBus()
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

    private fun client(
        breaker: CircuitBreaker = CircuitBreaker(),
        backoff: List<Long> = listOf(1, 1), // attempts = 3
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(RateLimitInterceptor(limiter))
            .addInterceptor(
                RetryInterceptor(
                    limiter = limiter,
                    bus = bus,
                    breaker = breaker,
                    backoffMs = backoff,
                ),
            ).connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
            .build()

    private fun get(): Request = Request.Builder().url(server.url("/v2/x")).build()

    @Test
    fun `5xx then success retries and recovers`() {
        server.enqueue(resp(500))
        server.enqueue(resp(500))
        server.enqueue(resp(200))
        val response = client().newCall(get()).execute() // 3 attempts: 500, 500, 200
        assertEquals(200, response.code)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `3 consecutive 5xx exhausts retries and throws`() {
        repeat(3) { server.enqueue(resp(500)) }
        val result = runCatching { client().newCall(get()).execute() }
        assertTrue(result.isFailure)
        assertEquals(3, server.requestCount) // 1 initial + 2 retries
    }

    @Test
    fun `429 sets cooldown and retries`() =
        runTest {
            server.enqueue(resp(429, retryAfter = "5"))
            server.enqueue(resp(200))
            val response = client().newCall(get()).execute()
            assertEquals(200, response.code)
            assertTrue(bus.events.first() is RateLimitEvent.CoolingDown)
        }

    @Test
    fun `circuit opens after 3 consecutive failures and short-circuits`() {
        val breaker = CircuitBreaker(failureThreshold = 3, openDurationMs = 60_000)
        val c = client(breaker, backoff = emptyList()) // 1 attempt per call → 1 failure per call
        repeat(3) { server.enqueue(resp(500)) }
        repeat(3) { runCatching { c.newCall(get()).execute() } }
        assertTrue(breaker.isOpen())

        val before = server.requestCount
        try {
            c.newCall(get()).execute()
            throw AssertionError("expected CircuitOpenException")
        } catch (_: CircuitOpenException) {
            // short-circuited, good
        }
        assertEquals(before, server.requestCount)
    }

    @Test
    fun `success resets the breaker`() {
        val breaker = CircuitBreaker(failureThreshold = 3, openDurationMs = 60_000)
        val c = client(breaker)
        server.enqueue(resp(500))
        server.enqueue(resp(500))
        server.enqueue(resp(200))
        assertEquals(200, c.newCall(get()).execute().code)
        assertFalse(breaker.isOpen())
        // a second full cycle must also succeed — breaker stays healthy after success
        server.enqueue(resp(500))
        server.enqueue(resp(500))
        server.enqueue(resp(200))
        assertEquals(200, c.newCall(get()).execute().code)
        assertFalse(breaker.isOpen())
    }

    @Test
    fun `post body content type is json`() {
        server.enqueue(resp(200))
        val body = "{\"context\":\"trending\"}".toRequestBody("application/json".toMediaType())
        client()
            .newCall(
                Request
                    .Builder()
                    .url(server.url("/v2/gifs/x/like"))
                    .put(body)
                    .build(),
            ).execute()
        val recorded = server.takeRequest()
        assertEquals("application/json", recorded.headers["Content-Type"]?.substringBefore(';'))
    }

    @Test
    fun `429 does not count toward circuit breaker`() {
        val breaker = CircuitBreaker(failureThreshold = 3, openDurationMs = 60_000)
        val c = client(breaker)
        // 3 attempts per call, all 429 (retryAfter 0 → min 5s cooldown); never a 200 → IOException.
        repeat(3) { server.enqueue(resp(429, retryAfter = "0")) }
        runCatching { c.newCall(get()).execute() }
        assertFalse(breaker.isOpen())
    }
}
