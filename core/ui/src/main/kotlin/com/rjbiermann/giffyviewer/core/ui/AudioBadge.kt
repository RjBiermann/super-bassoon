package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * "Has sound" VolumeUp badge for thumbnail corners (audit round-3 #5: was written
 * twice — mobile tile + TV card — with drifting padding). Caller supplies the
 * alignment/outer padding; this owns the badge look.
 */
@Composable
fun AudioBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.VolumeUp,
            contentDescription = "has sound",
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
    }
}

/** avgColor is "#rrggbb"; fall back to `fallback` on anything unexpected. */
fun avgColorOr(
    avgColor: String,
    fallback: Color,
): Color =
    try {
        Color(android.graphics.Color.parseColor(avgColor))
    } catch (_: IllegalArgumentException) {
        fallback
    }
