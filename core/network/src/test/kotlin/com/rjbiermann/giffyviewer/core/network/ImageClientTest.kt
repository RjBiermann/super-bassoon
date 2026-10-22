package com.rjbiermann.giffyviewer.core.network

import com.rjbiermann.giffyviewer.core.network.rate.RateLimitInterceptor
import com.rjbiermann.giffyviewer.core.network.rate.RetryInterceptor
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * #58: image traffic (Coil posters) must NOT ride the API's retry/breaker
 * path — a burst of failing poster loads recorded up to 4 breaker failures
 * per request and opened the circuit for EVERY feed at once ("Couldn't load
 * [feed]" everywhere). The image client keeps the shared rate limiter (the
 * ≤15/5s invariant covers media too), the browser identity and the auth
 * headers; only [RetryInterceptor] is stripped.
 */
class ImageClientTest {
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

    private fun ok(): MockResponse = MockResponse.Builder().code(200).body("{}").build()

    private fun get(): Request = Request.Builder().url(server.url("/v2/x")).build()

    @Test
    fun `image client strips the retry-breaker path and keeps limiter and identity`() {
        val network = buildNetwork(baseUrl = server.url("/").toString())
        val image = buildImageClient(network.client)
        assertFalse("RetryInterceptor must be stripped", image.interceptors.any { it is RetryInterceptor })
        assertTrue(
            "the shared limiter must still cover image requests (invariant covers media)",
            image.interceptors.any { it is RateLimitInterceptor },
        )
        assertTrue("browser identity headers must survive", image.interceptors.any { it is BrowserIdentityInterceptor })
        assertEquals(
            "exactly the retry layer is removed",
            network.client.interceptors.size - 1,
            image.interceptors.size,
        )
    }

    @Test
    fun `image requests keep the browser UA and bearer auth`() {
        val network = buildNetwork(baseUrl = server.url("/").toString(), authToken = { "tok" })
        val image = buildImageClient(network.client)
        server.enqueue(ok())
        val response = image.newCall(get()).execute()
        assertEquals(200, response.code)
        val recorded = server.takeRequest()
        assertEquals(BrowserIdentity.USER_AGENT, recorded.headers["User-Agent"])
        assertEquals("Bearer tok", recorded.headers["Authorization"])
    }

    @Test
    fun `an open circuit does not short-circuit image requests`() {
        val network = buildNetwork(baseUrl = server.url("/").toString())
        repeat(3) { network.breaker.recordFailure() }
        val image = buildImageClient(network.client)
        server.enqueue(ok())
        // The API client would return the synthetic retryable 503 here; the
        // image client has no breaker, so the request really goes out.
        val response = image.newCall(get()).execute()
        assertEquals(200, response.code)
        assertEquals("the image request must reach the server", 1, server.requestCount)
    }
}
