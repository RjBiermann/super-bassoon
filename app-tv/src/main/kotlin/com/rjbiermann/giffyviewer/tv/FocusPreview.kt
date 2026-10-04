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
 * inline autoplay (FeedScreen GifTile): poster stays composed UNDER the
 * surface, stop-before-swap, resume on re-settle. No watch-history writes
 * (screens own history).
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

    /** Focus settled on [gif] (600ms dwell already elapsed in the wiring). */
    fun onSettled(
        gif: Gif,
        dataSaver: Boolean,
    ) {
        if (dataSaver) return
        val p =
            player ?: playerFactory?.create(context)?.also { created ->
                created.volume = 0f
                created.repeatMode = Player.REPEAT_MODE_ONE
                player = created
            } ?: return
        val isNewGif = p.currentGifId.value != gif.id
        if (isNewGif) {
            // Mobile parity (stop-before-swap): clear the previous gif's last
            // frame BEFORE the surface moves to the new card — otherwise the
            // incoming card paints the stale frame during the re-attach window.
            p.stop()
        }
        settledId = gif.id
        if (isNewGif) {
            // Always the SD rendition: a preview must not burn HD data. The
            // gif-id cache key means the real player later replays from cache.
            p.playGif(gif, dataSaver = true)
        } else if (!p.isPlaying) {
            // Same gif re-settled (walked away and back): resume from the
            // stopped position, never restart from zero (mobile parity).
            p.prepare()
            p.play()
        }
    }

    /** The card with [gifId] lost focus (or went data-saver): stop — but ONLY
     *  if it is STILL the settled one. A late un-settle from a walked-away
     *  card must not kill a newer settle on another card (T5: card B's
     *  preview died when card A's un-settle landed after B's settle, with B's
     *  LaunchedEffect keys unchanged so nothing restarted it). stop() alone
     *  (no clearMediaItems) keeps the timeline + position for the resume path.
     */
    fun onUnsettled(gifId: String) {
        if (!clearsSettledPreview(settledId, gifId)) return
        settledId = null
        player?.stop()
    }

    fun release() {
        player?.release()
        // Null it out: a released player is unusable — the next settle must
        // recreate from the factory, not replay into a dead instance.
        player = null
        settledId = null
    }

    internal companion object {
        /** T5 guard (pure, unit-tested): an un-settle only acts when the
         *  un-settled card is STILL the settled one — never a different card
         *  that settled later, and never when nothing is settled. */
        internal fun clearsSettledPreview(
            settledId: String?,
            unsettingId: String,
        ): Boolean = settledId == unsettingId
    }
}
