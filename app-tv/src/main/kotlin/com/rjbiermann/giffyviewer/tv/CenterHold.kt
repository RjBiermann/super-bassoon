package com.rjbiermann.giffyviewer.tv

import android.os.SystemClock

/**
 * TV menu-less-remote keymap (AGENTS-APP "TV menu-less-remote keymap"): a
 * LONG PRESS on CENTER — press, then release after ≥[HOLD_MS]ms — fires
 * [onHold] on release, the same quick-actions panel MENU opens (one panel,
 * two openers). The action is evaluated on KeyUp, so the user does not have
 * to keep the button held until something happens: press-and-release with a
 * long duration is enough. A short tap stays the surface's existing CENTER
 * action; [up] returns true when the long press fired and the caller must
 * NOT also run the short-tap action (spec KeyUp-suppression rule — no double
 * play/pause or card open).
 *
 * One instance per focused node. [down] records the press start on the
 * initial (non-repeat) KeyDown — repeats must not touch it. State resets on
 * every press, so a swallowed KeyUp cannot leak into the next press.
 */
class CenterHold(
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val onHold: () -> Unit,
) {
    private var downAt = -1L

    /** Initial Center KeyDown: record the press start. Returns true (consumed). */
    fun down(): Boolean {
        downAt = now()
        return true
    }

    /** Center KeyUp: true = long press — [onHold] fired, suppress the short tap. */
    fun up(): Boolean {
        val longPress = downAt >= 0 && now() - downAt >= HOLD_MS
        downAt = -1L
        if (longPress) onHold()
        return longPress
    }
}

/** Long-press threshold (~500ms, mirroring the mobile long-press window). */
private const val HOLD_MS = 500L
