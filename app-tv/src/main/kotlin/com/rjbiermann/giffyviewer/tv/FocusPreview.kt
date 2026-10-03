package com.rjbiermann.giffyviewer.tv

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.player.GiffyPlayer
import com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory

/**
 * TV preview-on-focus (AGENTS-UX-PATTERNS "Preview-on-focus", the low-cost
 * advisory shape): only the SETTLED focus plays a preview — the card must hold
 * D-pad focus ≥600ms before anything starts, so fast focus walking never
 * decodes. ONE player instance is shared per screen and moved gif-to-gif
 * (no decoder churn), always muted + looping, SD rendition (preview is a
 * glance, not a watch). Data-saver keeps posters — same rule as the mobile
 * inline autoplay. No watch-history writes (screens own history).
 */
@Stable
internal class FocusPreview(
    private val playerFactory: GiffyPlayerFactory?,
    private val context: Context,
) {
    /** Id of the gif showing the preview (observable — cards bind on it). */
    var settledId by mutableStateOf<String?>(null)
        private set

    val enabled: Boolean
        get() = playerFactory != null

    /** The shared preview player — null until the first settle creates it. */
    var player by mutableStateOf<GiffyPlayer?>(null)
        private set

    /** Focus settled on [gif] (null = focus left / card scrolled out): stop. */
    fun onSettled(
        gif: Gif?,
        dataSaver: Boolean,
    ) {
        if (gif == null || dataSaver) {
            settledId = null
            player?.stop()
            player?.clearMediaItems()
            return
        }
        val p =
            player ?: playerFactory?.create(context)?.also { created ->
                created.volume = 0f
                created.repeatMode = Player.REPEAT_MODE_ONE
                player = created
            } ?: return
        settledId = gif.id
        // Always the SD rendition: a preview must not burn HD data. The
        // gif-id cache key means the real player later replays from cache.
        p.playGif(gif, dataSaver = true)
    }

    fun release() {
        player?.release()
        settledId = null
    }
}
