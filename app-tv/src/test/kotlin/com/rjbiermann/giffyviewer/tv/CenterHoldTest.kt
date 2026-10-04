package com.rjbiermann.giffyviewer.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CenterHold long-press keymap: fires WHILE HELD at the 500ms threshold. */
class CenterHoldTest {
    private class FakeScheduler : HoldScheduler {
        var scheduled: Runnable? = null
        var delayMs = -1L
        var fired = 0

        override fun schedule(
            runnable: Runnable,
            delayMs: Long,
        ) {
            scheduled = runnable
            this.delayMs = delayMs
        }

        override fun cancel(runnable: Runnable) {
            if (scheduled === runnable) scheduled = null
        }

        /** Simulates the main-looper timer elapsing while the key is held. */
        fun runTimer() {
            val r = scheduled
            scheduled = null
            r?.run()
        }
    }

    @Test
    fun `hold fires at 500ms while still held`() {
        var fired = 0
        val scheduler = FakeScheduler()
        val hold = CenterHold(scheduler) { fired += 1 }
        assertTrue(hold.down())
        assertEquals(HOLD_MS, scheduler.delayMs)
        assertEquals(0, fired) // nothing on KeyDown
        scheduler.runTimer() // timer elapses while the key is held
        assertEquals(1, fired)
    }

    @Test
    fun `keyup after a fired hold suppresses the tap and does not re-fire`() {
        var fired = 0
        val scheduler = FakeScheduler()
        val hold = CenterHold(scheduler) { fired += 1 }
        hold.down()
        scheduler.runTimer()
        assertEquals(1, fired)
        assertTrue(hold.up())
        assertEquals(1, fired) // no re-fire on release
        assertNull(scheduler.scheduled) // timer ownerless after fire+up
    }

    @Test
    fun `short tap cancels the timer and does not fire`() {
        var fired = 0
        val scheduler = FakeScheduler()
        val hold = CenterHold(scheduler) { fired += 1 }
        hold.down()
        assertFalse(hold.up())
        assertEquals(0, fired)
        scheduler.runTimer() // canceled — runnable gone
        assertEquals(0, fired)
    }

    @Test
    fun `state resets per press - swallowed keyup cannot leak into the next press`() {
        var fired = 0
        val scheduler = FakeScheduler()
        val hold = CenterHold(scheduler) { fired += 1 }
        hold.down()
        scheduler.runTimer() // KeyUp swallowed (panel stole focus) — no up()
        assertEquals(1, fired)
        hold.down() // next press resets
        assertFalse(hold.up()) // timer not elapsed in the new press
        assertEquals(1, fired)
    }
}
