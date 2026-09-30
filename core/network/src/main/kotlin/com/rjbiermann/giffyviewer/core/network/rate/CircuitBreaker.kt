package com.rjbiermann.giffyviewer.core.network.rate

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
    private var consecutiveFailures = 0
    private var openedUntil = 0L

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

/** Thread-safe holder so the OkHttp interceptor can share one breaker. */
class BreakerHolder {
    val breaker = AtomicReference(CircuitBreaker())
}
