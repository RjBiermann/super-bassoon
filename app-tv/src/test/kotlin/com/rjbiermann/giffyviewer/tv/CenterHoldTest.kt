package com.rjbiermann.giffyviewer.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** CenterHold long-press keymap: release-evaluated press duration. */
class CenterHoldTest {
    private class FakeHold {
        var fired = 0
        val onHold = { fired += 1 }
    }

    @Test
    fun `short tap does not fire and does not suppress the tap action`() {
        val hold = FakeHold()
        var clock = 0L
        val centerHold = CenterHold(now = { clock }, onHold = hold.onHold)
        centerHold.down()
        clock += 200
        assertFalse(centerHold.up())
        assertEquals(0, hold.fired)
    }

    @Test
    fun `long press fires on release and suppresses the tap action`() {
        val hold = FakeHold()
        var clock = 0L
        val centerHold = CenterHold(now = { clock }, onHold = hold.onHold)
        centerHold.down()
        clock += 500
        assertTrue(centerHold.up())
        assertEquals(1, hold.fired)
    }

    @Test
    fun `state resets per press - swallowed KeyUp cannot leak into the next press`() {
        val hold = FakeHold()
        var clock = 0L
        val centerHold = CenterHold(now = { clock }, onHold = hold.onHold)
        centerHold.down()
        clock += 1_000 // KeyUp swallowed (panel stole focus) — no up() call
        centerHold.down() // next press resets the clock
        clock += 100
        assertFalse(centerHold.up())
        assertEquals(0, hold.fired)
    }
}
