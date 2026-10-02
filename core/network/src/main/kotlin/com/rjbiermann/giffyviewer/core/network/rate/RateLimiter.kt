package com.rjbiermann.giffyviewer.core.network.rate

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** Published on [RateLimitBus] so the UI can show a "cooling down" indicator. */
sealed interface RateLimitEvent {
    /** Requests are throttled until this epoch-millis (429 cooldown or circuit open). */
    data class CoolingDown(
        val untilEpochMs: Long,
        val reason: String,
    ) : RateLimitEvent

    data object Recovered : RateLimitEvent
}

/** Simple in-process event fan-out; UI collects this for the indicator.
 *  ponytail audit (2026-10): no wrapper class — the MutableSharedFlow IS the bus. */
typealias RateLimitBus = kotlinx.coroutines.flow.MutableSharedFlow<RateLimitEvent>

fun RateLimitBus.publish(event: RateLimitEvent) {
    tryEmit(event)
}

/**
 * Rolling-window rate limiter.
 *
 * Hard invariant (PLAN §4): never more than [maxPerWindow] requests in any rolling
 * [windowMs] window. This is stricter than a classic token bucket — a 10-burst + 2/s
 * refill bucket can legally produce 20 requests inside one 5-second window, which
 * violates the invariant. Rolling-window gives max burst [maxPerWindow] and a flat
 * 2 req/s sustained (10 per 5s) — both plan numbers in one mechanism.
 */
class RollingWindowRateLimiter(
    private val maxPerWindow: Int = 10,
    private val windowMs: Long = 5_000,
    private val now: () -> Long = System::currentTimeMillis,
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
) {
    private val windowStarts = ArrayDeque<Long>()
    private val mutex = Mutex()

    /** Cooldown from a 429 Retry-After; requests wait out this instant before any slot. */
    private val cooldownUntil = AtomicLong(0)

    /** Called by the 429 handler. Clamps per PLAN §4: min 5s, max retryAfter + 30s. */
    fun startCooldown(
        retryAfterMs: Long,
        nowMs: Long = now(),
    ) {
        val clamped = retryAfterMs.coerceIn(MIN_COOLDOWN_MS, retryAfterMs + MAX_COOLDOWN_EXTRA_MS)
        cooldownUntil.updateAndGet { current -> maxOf(current, nowMs + clamped) }
    }

    /** Runs [block] as one accounted request, delaying as needed to keep the invariant. */
    suspend fun <T> acquire(block: suspend () -> T): T {
        acquireSlot()
        return block()
    }

    /** Reserves a slot without executing anything (used before retry sleeps). */
    suspend fun acquireSlot() {
        waitForSlot()
    }

    private suspend fun waitForSlot() {
        while (true) {
            val cooldown = cooldownUntil.get() - now()
            if (cooldown > 0) {
                delayFn(cooldown)
                continue
            }
            var granted = false
            var waitMs = 0L
            mutex.withLock {
                val nowMs = now()
                while (windowStarts.isNotEmpty() && nowMs - windowStarts.first() >= windowMs) {
                    windowStarts.removeFirst()
                }
                if (windowStarts.size < maxPerWindow) {
                    windowStarts.addLast(nowMs)
                    granted = true
                } else {
                    waitMs = windowMs - (nowMs - windowStarts.first())
                }
            }
            if (granted) return
            // Sleep outside the lock so other callers can also queue.
            delayFn(waitMs.coerceAtLeast(1))
        }
    }

    companion object {
        private const val MIN_COOLDOWN_MS = 5_000L
        private const val MAX_COOLDOWN_EXTRA_MS = 30_000L
    }
}
