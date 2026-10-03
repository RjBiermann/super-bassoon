package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.ui.graphics.Color

/**
 * Over-video overlay tokens (audit batch 15, F6a): white/black over video is
 * the media-overlay standard and stays — what drifted was the ad-hoc alphas
 * (0.55/0.6/0.3/0.7 across the two independently built player layers). One
 * token set, used by BOTH player screens (mobile PlayerScreen + TvPlayerScreen).
 */
object PlayerOverlay {
    /** Scrims, chips, badges and dialog backdrops over video. */
    val scrim = Color.Black.copy(alpha = 0.55f)

    /** Unfilled progress track / hairline borders over video (30%). */
    val track = Color.White.copy(alpha = 0.3f)

    /** Secondary text on video — descriptions, seek flash (70%). */
    val secondaryOnVideo = Color.White.copy(alpha = 0.7f)
}
