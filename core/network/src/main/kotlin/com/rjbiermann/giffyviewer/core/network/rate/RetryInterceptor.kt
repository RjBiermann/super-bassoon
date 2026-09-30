package com.rjbiermann.giffyviewer.core.network.rate

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import kotlin.random.Random

/**
 * OkHttp-level rate handling (PLAN §4).
 *
 * Order: [RateLimitInterceptor] (slot + cooldown) → breaker/retry via [RetryInterceptor].
 * - 429: honor Retry-After (clamp min 5s, max retryAfter + 30s), publish CoolingDown, retry.
 * - 5xx: backoff 1s/4s/15s + jitter, max 3 retries; feeds the circuit breaker.
 * - IOException: feeds the circuit breaker, retried too (network blips).
 */
class RetryInterceptor(
    private val limiter: RollingWindowRateLimiter,
    private val bus: RateLimitBus,
    private val breaker: CircuitBreaker,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val backoffMs: List<Long> = listOf(1_000, 4_000, 15_000),
    private val jitter: Random = Random.Default,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (breaker.isOpen()) {
            // The open circuit surfaces as a retryable 503 — NEVER a thrown
            // exception. A throw here escapes OkHttp's worker thread and
            // crashes the app (live crash observed 2026-09-30).
            val retryAfterS =
                (((breaker.retryAtEpochMs() - System.currentTimeMillis()).coerceAtLeast(0)) / 1_000)
                    .toString()
            return circuitOpenResponse(request, retryAfterS)
        }

        var lastException: IOException? = null

        val attempts = backoffMs.size + 1 // 1 initial + N retries
        for (attempt in 0 until attempts) {
            if (attempt > 0) {
                val delayMs = jitter.jittered(backoffMs[attempt - 1])
                // reserve a slot before sleeping, stays within invariant (intercept is blocking)
                kotlinx.coroutines.runBlocking { limiter.acquireSlot() }
                sleep(delayMs)
            }
            try {
                val response = chain.proceed(request)
                when {
                    response.code in 500..599 -> {
                        breaker.recordFailure()
                        response.close() // failed responses are never returned; close to free the connection
                        lastException = IOException("HTTP ${response.code} (attempt $attempt)")
                    }

                    response.code == 429 -> {
                        val retryAfterMs =
                            response
                                .header("Retry-After")
                                ?.toLongOrNull()
                                ?.times(1_000) ?: DEFAULT_RETRY_AFTER_MS
                        val clamped = retryAfterMs.coerceIn(MIN_COOLDOWN_MS, retryAfterMs + MAX_COOLDOWN_EXTRA_MS)
                        limiter.startCooldown(retryAfterMs)
                        bus.publish(
                            RateLimitEvent.CoolingDown(
                                untilEpochMs = System.currentTimeMillis() + clamped,
                                reason = "rate limited (429)",
                            ),
                        )
                        // 429 is throttling, not a failure — does NOT count toward the breaker.
                        response.close()
                    }

                    else -> {
                        breaker.recordSuccess()
                        bus.publish(RateLimitEvent.Recovered)
                        return response
                    }
                }
            } catch (e: IOException) {
                breaker.recordFailure()
                lastException = e
            }
        }
        throw lastException ?: IOException("max retries exhausted")
    }

    companion object {
        private const val DEFAULT_RETRY_AFTER_MS = 5_000L
        private const val MIN_COOLDOWN_MS = 5_000L
        private const val MAX_COOLDOWN_EXTRA_MS = 30_000L

        private fun Random.jittered(base: Long): Long = (base * (0.5 + nextDouble() * 0.5)).toLong().coerceAtLeast(1)
    }

    /** Synthetic retryable response while the circuit is open (PLAN §4). */
    private fun circuitOpenResponse(
        request: Request,
        retryAfterS: String,
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(503)
            .message("circuit open")
            .body(ResponseBody.create(null, "circuit open"))
            .header("Retry-After", retryAfterS)
            .build()
}

/**
 * Acquires a window slot before every call so the ≤10-per-5s invariant holds
 * regardless of retries. Must be the first interceptor in the chain.
 */
class RateLimitInterceptor(
    private val limiter: RollingWindowRateLimiter,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        kotlinx.coroutines.runBlocking {
            limiter.acquire { chain.proceed(chain.request()) }
        }
}
