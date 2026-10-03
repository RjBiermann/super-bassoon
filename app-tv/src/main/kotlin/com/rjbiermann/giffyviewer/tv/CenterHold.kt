package com.rjbiermann.giffyviewer.tv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TV menu-less-remote keymap (AGENTS-APP "TV menu-less-remote keymap"): on
 * remotes without a MENU key (projectors, basic smart-TV remotes), press-and-
 * hold CENTER ≥500ms fires [onHold] — the same quick-actions panel MENU opens
 * (one panel, two openers). A short tap stays the surface's existing CENTER
 * action; the caller decides both.
 *
 * One instance per focused node. [down] starts the hold clock on the initial
 * (non-repeat) KeyDown — repeats must not restart it. [up] on KeyUp reports
 * whether the hold fired: when it did, the caller must NOT also run the
 * short-tap action (spec KeyUp-suppression rule — no double play/pause or
 * card open). State resets on the next press, so a panel opening that steals
 * focus and swallows the KeyUp cannot leak into the next press.
 */
class CenterHold(
    private val scope: CoroutineScope,
    private val onHold: () -> Unit,
) {
    private var job: Job? = null
    private var fired = false

    /** Initial Center KeyDown: start the ~500ms hold clock. Returns true (consumed). */
    fun down(): Boolean {
        job?.cancel()
        fired = false
        job =
            scope.launch {
                delay(HOLD_MS)
                fired = true
                onHold()
            }
        return true
    }

    /** Center KeyUp: true = the hold fired — suppress the short-tap action. */
    fun up(): Boolean {
        job?.cancel()
        val suppress = fired
        fired = false
        return suppress
    }
}

/** Hold threshold (~500ms, mirroring the mobile long-press window). */
private const val HOLD_MS = 500L
