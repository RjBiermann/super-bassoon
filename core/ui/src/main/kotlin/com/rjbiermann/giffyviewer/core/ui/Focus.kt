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
 * TV focus treatment (PLAN §9 TV: accent outline on focus; the borrowed
 * hover-fill pattern → focus-fill). No-op on touch (focus only lands via
 * D-pad/keyboard). Pass the row's [interactionSource] when the node is also
 * clickable — two focusable modifiers in one chain race and the inner one
 * never sees focus (the missing-border bug).
 */
fun Modifier.giffyFocus(
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    fillOnFocus: Color? = null,
): Modifier =
    composed {
        val interaction =
            interactionSource ?: remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val border by animateColorAsState(
            if (focused) GiffyColors.BrandRed else Color.Transparent,
            label = "giffyFocusBorder",
        )
        val fill by animateColorAsState(
            if (focused) fillOnFocus ?: Color.Transparent else Color.Transparent,
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
        shape = RoundedCornerShape(24.dp),
        colors =
            ButtonDefaults.buttonColors(
                // Rest = primaryContainer fill + TextHigh (audit: red-on-black was
                // 3.5:1 < AA); focus = brand red fill + white.
                containerColor = if (focused) GiffyColors.BrandRed else GiffyColors.TextHigh.copy(alpha = 0.08f),
                contentColor = if (focused) Color.White else GiffyColors.TextHigh,
            ),
        border = BorderStroke(1.dp, GiffyColors.BrandRed),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text)
    }
}
