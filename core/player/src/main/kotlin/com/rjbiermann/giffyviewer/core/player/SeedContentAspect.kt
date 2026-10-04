package com.rjbiermann.giffyviewer.core.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * SLICE-14/16: seed the PlayerView's content frame aspect from the gif's OWN
 * width/height metadata at switch time. Before the video resolves,
 * AspectRatioFrameLayout has no aspect — with RESIZE_MODE_ZOOM/FILL that
 * degenerates to fill-frame (the reported stretch warp on next/prev), and a
 * PREVIOUS video's aspect would keep warping the NEXT gif until its own size
 * arrives. The PlayerView's live videoSize update (PlayerView
 * .updateAspectRatio → onContentAspectRatioChanged → frame.setAspectRatio)
 * overrides the seed as soon as the real size reports. `null`-safe: no
 * content frame (custom layout) → a no-op, current behavior unchanged.
 *
 * `ratio == 0` (unknown gif dimensions, per [contentAspectRatio]) = CLEAR:
 * AspectRatioFrameLayout's default `aspectRatio` field is 0, so 0 restores
 * the pre-seed behavior — never a stale or invented aspect.
 */
@OptIn(UnstableApi::class)
fun PlayerView.seedContentAspectRatio(ratio: Float) {
    findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)?.setAspectRatio(ratio)
}
