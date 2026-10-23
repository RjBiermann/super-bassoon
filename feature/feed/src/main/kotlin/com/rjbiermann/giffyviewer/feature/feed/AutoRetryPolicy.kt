package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/** Issue #46 part 2: consecutive silent auto-retries allowed per surface appearance. */
const val AUTO_RETRY_MAX = 2

/** Issue #46 part 2: debounce before an auto-retry fires (fast dismissals
 *  cancel before this; the rate limiter/breaker still guard the network). */
const val AUTO_RETRY_DEBOUNCE_MS = 2_000L

/** Issue #46 part 2 retry gate: the surface may auto-retry while under the cap;
 *  the caller's counter resets on success (and per appearance, via remember). */
fun shouldAutoRetry(consecutiveRetries: Int): Boolean = consecutiveRetries < AUTO_RETRY_MAX

/**
 * Issue #63 follow-up: one surface appearance's auto-retry budget. A failure
 * while offline can never succeed (each attempt also pays interceptor backoff),
 * so the offline→online transition re-arms the budget — that reconnect is exactly
 * when the silent recovery is supposed to clear the card. A continuously-online
 * failure keeps the [AUTO_RETRY_MAX] cap so the card cannot loop.
 */
class AutoRetryBudget(
    isOnline: Boolean,
) {
    private var retries = 0

    var isOnline: Boolean = isOnline
        set(value) {
            if (value && !field) retries = 0
            field = value
        }

    /** Consumes one attempt; false once the budget is spent. */
    fun take(): Boolean {
        if (!shouldAutoRetry(retries)) return false
        retries++
        return true
    }

    /** A completed load re-arms the budget. */
    fun reset() {
        retries = 0
    }
}

/**
 * Issue #46 part 2: silent recovery for surfaces stuck in a failure state
 * (paging refresh error, explore creators fetch, one-shot taxonomy load —
 * issue #49's silent-empty niches page). When [error] is visible — meaning
 * the surface composed/is visible — fire ONE debounced [retry] through the
 * caller's existing retry path, up to [AUTO_RETRY_MAX] per success streak;
 * a success resets the counter, a reload-in-progress ([success] = false,
 * [error] = false) preserves it so retry→loading→error can't loop. Re-fires
 * on a NEW failure (keyed by [key]) or when connectivity returns
 * ([isOnline] flips true, which also re-arms the budget via [AutoRetryBudget]).
 * No WorkManager, no polling —
 * stale-while-revalidate on open, network truth from the shared NetworkMonitor.
 */
@Composable
fun AutoRetryEffect(
    error: Boolean,
    success: Boolean,
    key: Any?,
    isOnline: Boolean,
    retry: () -> Unit,
) {
    // Plain state, not snapshot state: only this effect touches it.
    val budget = remember { AutoRetryBudget(isOnline) }
    LaunchedEffect(key, isOnline) {
        budget.isOnline = isOnline
        when {
            success -> budget.reset()
            error && budget.take() -> {
                delay(AUTO_RETRY_DEBOUNCE_MS)
                retry()
            }
        }
    }
}
