package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Issue #33: the mobile state carrier — each shell's activity provides it
 *  from NetworkMonitor. Default true (= online): un-provided hosts (TV keeps
 *  its root overlay; previews) never render a phantom banner. */
val LocalIsOnline = compositionLocalOf { true }

/**
 * Issue #33: thin non-interactive connectivity banner ("You're offline —
 * showing cached gifs"). Presentational only: the mounts decide visibility
 * with [offlineBannerVisible] driven by [NetworkMonitor]. errorContainer is
 * the M3 state surface for outages (scheme role only — design tokens live in
 * Theme.kt). Non-focusable by construction (no click/ripple).
 *
 * Height is a MINIMUM, not fixed: at large system font scales the copy wraps
 * and the strip grows — a fixed 40dp strip truncated the sentence instead.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 40.dp)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "You're offline — showing cached gifs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center,
        )
    }
}

/** Banner rule, one place for both mounts (self-check in OfflineBannerTest).
 *  Public for the app-shell mount sites (:core:ui is its own module). */
fun offlineBannerVisible(isOnline: Boolean): Boolean = !isOnline
