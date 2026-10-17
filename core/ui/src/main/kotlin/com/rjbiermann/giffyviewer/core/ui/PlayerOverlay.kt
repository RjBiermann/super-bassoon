package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.ui.graphics.Color

/**
 * Over-video overlay tokens (audit batch 15, F6a + issue-#7 art-scrim rule,
 * DESIGN-theme-stream.md §7): scrims over artwork = black at 40–60% alpha
 * with TEXT in onSurface #F7E9EC — 60% since issue #51 (contrast finding: the
 * onSurface text needs the heavier in-band scrim under bright artwork);
 * track/hairline strokes are not text and keep the white media-overlay
 * standard (the F6a class). One token set, used by BOTH player screens
 * (mobile PlayerScreen + TvPlayerScreen).
 */
object PlayerOverlay {
    /** Scrims, chips, badges and dialog backdrops over video (issue band: 40–60%). */
    val scrim = Color.Black.copy(alpha = 0.60f)

    /** Unfilled progress track / hairline borders over video (30%). */
    val track = Color.White.copy(alpha = 0.3f)

    /** Secondary text on video — descriptions (issue rule: onSurface tint,
     *  alpha carried by the token). Issue #51 contrast finding: 70% fell to
     *  ≈2–3.7:1 over bright artwork inside the gradient ramp — near-opaque
     *  onSurface (≥0.95) is the report's floor for text in that zone. */
    val secondaryOnVideo = GiffyColors.OnSurface.copy(alpha = 0.95f)

    /** Dark pill backdrop (~80% black, WCAG-tested via OverlayContrast) set
     *  BEHIND secondary text rows over video — finding-5 residual: scheme
     *  secondary #E3BDC4 is only ≈3.4:1 under the 0.60 scrim on white frames;
     *  with this backdrop under it the composite clears AA by a wide margin.
     *  The text color stays as-is; the backdrop carries the contrast. */
    val secondaryOnVideoBackdrop = Color.Black.copy(alpha = 0.80f)
}
