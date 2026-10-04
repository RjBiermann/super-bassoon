package com.rjbiermann.giffyviewer.tv

import android.os.Handler
import android.os.Looper

/** Who schedules/runs the hold timer — production is the main-looper
 *  [android.os.Handler]; unit tests inject a fake and fire the runnable
 *  themselves (a real wall-clock hold is not testable on the JVM). */
interface HoldScheduler {
    fun schedule(
        runnable: Runnable,
        delayMs: Long,
    )

    fun cancel(runnable: Runnable)
}

/** Production scheduler: android.os.Handler on the main looper. */
object MainHoldScheduler : HoldScheduler {
    // lazy: unit tests never touch it (android.jar is a stub on the JVM).
    private val handler: Handler by lazy { Handler(Looper.getMainLooper()) }

    override fun schedule(
        runnable: Runnable,
        delayMs: Long,
    ) {
        handler.postDelayed(runnable, delayMs)
    }

    override fun cancel(runnable: Runnable) {
        handler.removeCallbacks(runnable)
    }
}

/**
 * TV menu-less-remote keymap (AGENTS-APP "TV menu-less-remote keymap"): a
 * LONG PRESS on CENTER fires [onHold] at [HOLD_MS] WHILE STILL HELD —
 * standard long-press semantics (user rework 2026-10: earlier the fire was
 * release-evaluated, which made the panel react only on KeyUp). The timer
 * fires ~[HOLD_MS]ms after the initial KeyDown; [up] cancels the timer if
 * the hold has not fired yet (short tap → the surface's own CENTER action
 * stays) and returns true when a hold FIRED so the caller must NOT also run
 * the short-tap action (spec KeyUp-suppression rule — no double play/pause
 * or card open). KeyUp after a fired hold does not re-fire.
 *
 * One instance per focused node. [down] is for the initial (non-repeat)
 * KeyDown — repeats must not touch it. State resets on every press, so a
 * swallowed KeyUp cannot leak into the next press.
 */
class CenterHold(
    private val scheduler: HoldScheduler = MainHoldScheduler,
    private val onHold: () -> Unit,
) {
    private var down = false

    /** Scheduled at the initial KeyDown; fires the hold while the key is held. */
    private val fireHold =
        Runnable {
            if (down) {
                fired = true
                onHold()
            }
        }

    private var fired = false

    /** Initial Center KeyDown: start the hold clock. Returns true (consumed). */
    fun down(): Boolean {
        down = true
        fired = false
        scheduler.schedule(fireHold, HOLD_MS)
        return true
    }

    /** Center KeyUp: cancels an unfired timer; true = a hold fired (suppress
     *  the short tap) and this KeyUp does NOT re-fire [onHold]. */
    fun up(): Boolean {
        scheduler.cancel(fireHold)
        val hold = fired
        down = false
        fired = false
        return hold
    }
}

/** Long-press threshold (~500ms, mirroring the mobile long-press window). */
internal const val HOLD_MS = 500L
