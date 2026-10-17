package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Pure WCAG 2.x math for the overlay backdrop token (a11y finding-5 residual,
 * DESIGN-A11YFIX.md): verifies PlayerOverlay.secondaryOnVideoBackdrop lifts
 * secondary overlay text to ≥4.5:1 over the worst-case white video frame.
 */
object OverlayContrast {
    /** What the eye sees where [overlay] (with its own alpha) stacks over [base]. */
    fun composite(
        base: Color,
        overlay: Color,
    ): Color {
        val a = overlay.alpha

        return Color(
            red = overlay.red * a + base.red * (1 - a),
            green = overlay.green * a + base.green * (1 - a),
            blue = overlay.blue * a + base.blue * (1 - a),
        )
    }

    /** WCAG 2.x relative-luminance contrast ratio between two opaque colors (1..21). */
    fun contrastRatio(
        fg: Color,
        bg: Color,
    ): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }

        fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

        val l1 = max(luminance(fg), luminance(bg))
        val l2 = min(luminance(fg), luminance(bg))
        return (l1 + 0.05) / (l2 + 0.05)
    }
}
