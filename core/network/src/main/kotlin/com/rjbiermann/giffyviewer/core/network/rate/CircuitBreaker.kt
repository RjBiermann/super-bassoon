package com.rjbiermann.giffyviewer.core.network.rate

import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicReference

/** Thrown when the circuit breaker is open — callers surface this as a retryable error. */
class CircuitOpenException(
    val retryAtEpochMs: Long,
) : IllegalStateException("Circuit open until $retryAtEpochMs")

/**
 * Circuit breaker (PLAN §4): 3 consecutive failures → open 5 minutes.
 * A failure is an IOException or any 5xx response; any success resets the count.
 */
class CircuitBreaker(
    private val failureThreshold: Int = 3,
    private val openDurationMs: Long = 5 * 60 * 1_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    // Issue #63: written from the OkHttp threads AND from the connectivity
    // callback (BreakerReconnectReset), so both fields must be visible.
    @Volatile private var consecutiveFailures = 0

    @Volatile private var openedUntil = 0L

    /** True if a call may proceed; false (or throw from [checkOrThrow]) while open. */
    fun isOpen(): Boolean = now() < openedUntil

    fun retryAtEpochMs(): Long = openedUntil

    fun checkOrThrow() {
        if (isOpen()) throw CircuitOpenException(openedUntil)
    }

    fun recordFailure() {
        consecutiveFailures++
        if (consecutiveFailures >= failureThreshold) {
            openedUntil = now() + openDurationMs
        }
    }

    fun recordSuccess() {
        consecutiveFailures = 0
        openedUntil = 0
    }
}

/**
 * Issue #63: the breaker's cooldown is time-based and nothing else generates
 * traffic while it is parked, so a reconnect alone could never release it —
 * the two AutoRetryEffect retries after airplane mode both hit the synthetic
 * "503 circuit open" and the error card latched for the whole cooldown.
 * Resets ONLY on the offline -> online flip (repeated online events, e.g. a
 * capabilities change on a healthy network, prove nothing about the breaker).
 */
class BreakerReconnectReset(
    private val breaker: CircuitBreaker,
    private val isOnline: StateFlow<Boolean>,
) {
    private var wasOnline: Boolean = isOnline.value

    fun onConnectivityChanged(online: Boolean) {
        if (!wasOnline && online) breaker.recordSuccess()
        wasOnline = online
    }

    /** App-singleton lifetime: one collector per process (the shells' Application scope). */
    suspend fun observe() {
        isOnline.collect(::onConnectivityChanged)
    }
}

/** Thread-safe holder so the OkHttp interceptor can share one breaker. */
class BreakerHolder {
    val breaker = AtomicReference(CircuitBreaker())
}
