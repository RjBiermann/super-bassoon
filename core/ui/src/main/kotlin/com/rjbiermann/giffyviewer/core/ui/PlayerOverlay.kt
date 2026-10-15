package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.ui.graphics.Color

/**
 * Over-video overlay tokens (audit batch 15, F6a + issue-#7 art-scrim rule,
 * DESIGN-theme-stream.md §7): scrims over artwork = black at 40–60% alpha
 * with TEXT in onSurface #F7E9EC — 55% remains the chosen value; track/
 * hairline strokes are not text and keep the white media-overlay standard
 * (the F6a class). One token set, used by BOTH player screens (mobile
 * PlayerScreen + TvPlayerScreen).
 */
object PlayerOverlay {
    /** Scrims, chips, badges and dialog backdrops over video (issue band: 40–60%). */
    val scrim = Color.Black.copy(alpha = 0.55f)

    /** Unfilled progress track / hairline borders over video (30%). */
    val track = Color.White.copy(alpha = 0.3f)

    /** Secondary text on video — descriptions (issue rule: onSurface tint,
     *  alpha carried by the token; #F7E9EC@70% ≈ white@70% visually). */
    val secondaryOnVideo = GiffyColors.OnSurface.copy(alpha = 0.7f)
}
