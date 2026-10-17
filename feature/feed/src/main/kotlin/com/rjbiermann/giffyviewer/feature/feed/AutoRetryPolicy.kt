package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Issue #46 part 2: silent recovery for surfaces stuck in a failure state
 * (paging refresh error, explore creators fetch, one-shot taxonomy load —
 * issue #49's silent-empty niches page). When [error] is visible — meaning
 * the surface composed/is visible — fire ONE debounced [retry] through the
 * caller's existing retry path, up to [AUTO_RETRY_MAX] per success streak;
 * a success resets the counter, a reload-in-progress ([success] = false,
 * [error] = false) preserves it so retry→loading→error can't loop. Re-fires
 * on a NEW failure (keyed by [key]) or when connectivity returns
 * ([isOnline] flips true). No WorkManager, no polling —
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
    var consecutiveRetries by remember { mutableStateOf(0) }
    LaunchedEffect(key, isOnline) {
        when {
            success -> consecutiveRetries = 0
            error && shouldAutoRetry(consecutiveRetries) -> {
                consecutiveRetries++
                delay(AUTO_RETRY_DEBOUNCE_MS)
                retry()
            }
        }
    }
}
