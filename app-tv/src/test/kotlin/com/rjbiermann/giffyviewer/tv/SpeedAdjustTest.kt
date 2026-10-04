package com.rjbiermann.giffyviewer.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/** SLICE-13: D-pad speed slider steps — quarter-grid snap + [0.5, 2.0] clamp. */
class SpeedAdjustTest {
    @Test
    fun `right steps up by quarter`() {
        assertEquals(1.25f, adjustSpeed(1f, SPEED_STEP), 0.0001f)
    }

    @Test
    fun `left steps down by quarter`() {
        assertEquals(0.75f, adjustSpeed(1f, -SPEED_STEP), 0.0001f)
    }

    @Test
    fun `clamped to the mobile slider range`() {
        assertEquals(0.5f, adjustSpeed(0.5f, -SPEED_STEP), 0.0001f)
        assertEquals(2f, adjustSpeed(2f, SPEED_STEP), 0.0001f)
    }

    @Test
    fun `quarter grid snaps floating-point drift`() {
        // 0.5 + 6×0.25 accumulates fp error; every result stays on the grid.
        var speed = 0.5f
        repeat(6) { speed = adjustSpeed(speed, SPEED_STEP) }
        assertEquals(2f, speed, 0.0001f)
    }
}
