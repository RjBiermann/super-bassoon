package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * TV focus treatment for M3 components hosted on TV (issue-#7 focus tokens,
 * DESIGN-theme-stream.md §3.1: focused fill = `primaryContainer` #7D0030,
 * focused border/glow = `primary` #FF668F; M3 chips draw their own rest
 * ring at `outlineVariant` #51444A). tv-material components carry these
 * tokens natively — this modifier remains ONLY where tv-material has no
 * shared-module counterpart (SettingsScreen/CustomFeedsScreen chip rows).
 * No-op on touch (focus only lands via D-pad/keyboard). Pass the row's
 * [interactionSource] when the node is also clickable — two focusable
 * modifiers in one chain race and the inner one never sees focus (the
 * missing-border bug).
 */
fun Modifier.giffyFocus(
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = RoundedCornerShape(12.dp),
): Modifier =
    composed {
        val interaction =
            interactionSource ?: remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val border by animateColorAsState(
            // Role read (audit batch 15, F5) — not a direct GiffyColors read.
            if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
            label = "giffyFocusBorder",
        )
        val fill by animateColorAsState(
            if (focused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            label = "giffyFocusFill",
        )
        this
            .then(if (interactionSource == null) Modifier.focusable() else Modifier)
            .background(fill, shape)
            .border(3.dp, border, shape)
    }

/**
 * Site button pattern (§9): accent OUTLINE at rest, accent FILL with inverse
 * text on focus (TV) / press (touch) — 12dp-rounded secondary buttons.
 */
@Composable
fun GiffyPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        // Shape on the M3 scale (batch 15, F7): 24 → extraLarge.
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            ButtonDefaults.buttonColors(
                // Rest = neutral fill (+ onSurface text; audit: red-on-black was
                // 3.5:1 < AA); focus = primaryContainer fill + onPrimaryContainer
                // text — the issue-#7 focus tokens (DESIGN-theme-stream.md §3.1).
                // Role reads (audit batch 15, F5) — no direct palette access.
                containerColor =
                    if (focused) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    },
                contentColor =
                    if (focused) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text)
    }
}
