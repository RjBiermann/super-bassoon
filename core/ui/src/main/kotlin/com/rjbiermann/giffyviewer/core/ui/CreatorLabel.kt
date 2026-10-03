package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "@username" + verified tick, one shared idiom (2026-10 round-3 audit: collapsed
 * 10 hand-rolled copies whose padding/size/tint had drifted). `tint` recolors the
 * text (Color.Unspecified keeps the ambient content color); `tickTint` recolors
 * the tick (falls back to the shared Info cyan); `tickSize` follows the label
 * size (14dp default, 12dp TV cards, 16–18dp player clusters).
 */
@Composable
fun CreatorLabel(
    username: String,
    verified: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    tickTint: Color = Color.Unspecified,
    tickSize: Dp = 14.dp,
    /** Non-null = the label navigates (links audit: @user → creator feed). */
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            if (onClick != null) {
                modifier.clickable(onClick = onClick)
            } else {
                modifier
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "@$username", color = tint, style = style)
        if (verified) {
            VerifiedTick(
                // Role read (audit batch 15, F5): scheme tertiary = shared Info
                // cyan; direct palette reads stay out of composables.
                tint = tickTint.takeOrElse { MaterialTheme.colorScheme.tertiary },
                modifier = Modifier.padding(start = 4.dp).size(tickSize),
            )
        }
    }
}
