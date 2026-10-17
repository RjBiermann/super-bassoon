package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding-5 residual (DESIGN-A11YFIX.md): scheme secondary #E3BDC4 overlay
 * text is ≈3.4:1 under the 0.60 scrim on a white video frame. With the
 * PlayerOverlay.secondaryOnVideoBackdrop pill composited under the text the
 * WCAG ratio must clear AA (≥4.5:1).
 */
class OverlayContrastTest {
    @Test
    fun `scrim alone leaves secondary text below AA on white frame`() {
        val underScrim = OverlayContrast.composite(Color.White, PlayerOverlay.scrim)
        val ratio = OverlayContrast.contrastRatio(GiffyColors.Secondary, underScrim)
        assertTrue("expected <4.5, got $ratio", ratio < 4.5)
    }

    @Test
    fun `backdrop over scrim over white frame meets AA for secondary text`() {
        val underScrim = OverlayContrast.composite(Color.White, PlayerOverlay.scrim)
        val underText = OverlayContrast.composite(underScrim, PlayerOverlay.secondaryOnVideoBackdrop)
        val ratio = OverlayContrast.contrastRatio(GiffyColors.Secondary, underText)
        assertTrue("expected ≥4.5, got $ratio", ratio >= 4.5)
    }

    @Test
    fun `composite of fully transparent overlay keeps base`() {
        assertEquals(Color.White, OverlayContrast.composite(Color.White, Color.Black.copy(alpha = 0f)))
        assertEquals(Color.White, OverlayContrast.composite(Color.Black, Color.White.copy(alpha = 1f)))
    }
}
