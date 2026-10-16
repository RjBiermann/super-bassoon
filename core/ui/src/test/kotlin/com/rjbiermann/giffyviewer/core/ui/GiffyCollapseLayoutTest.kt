package com.rjbiermann.giffyviewer.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #40 regression on the collapse re-claim geometry: the grown measure
 *  must exactly match the consumed collapse offset, so (a) a fill-max
 *  collapsed content's bottom stays reachable at the un-grown parent bottom
 *  and (b) no phantom gap opens below the grid at rest or while scrolled. */
class GiffyCollapseLayoutTest {
    private fun bottomWhenFilling(spec: CollapseLayoutSpec): Int {
        // A content that fills the grown max heights; drawn at contentOffsetY.
        return spec.measureMaxHeight + spec.contentOffsetY
    }

    @Test
    fun `zero offset is a no-op`() {
        val spec = collapseLayout(incomingMinHeight = 0, incomingMaxHeight = 900, collapseOffsetPx = 0)
        assertEquals(0, spec.measureMinHeight)
        assertEquals(900, spec.measureMaxHeight)
        assertEquals(0, spec.contentOffsetY)
    }

    @Test
    fun `grown height exactly matches consumed offset`() {
        val spec = collapseLayout(incomingMinHeight = 0, incomingMaxHeight = 900, collapseOffsetPx = 300)
        assertEquals(300, spec.measureMinHeight)
        assertEquals(1200, spec.measureMaxHeight)
        assertEquals(-300, spec.contentOffsetY)
        // (b) no phantom gap: bottom edge sits exactly at the un-grown bottom.
        assertEquals(900, bottomWhenFilling(spec))
    }

    @Test
    fun `full collapse keeps content bottom reachable`() {
        val spec = collapseLayout(incomingMinHeight = 140, incomingMaxHeight = 1200, collapseOffsetPx = 500)
        // (a) the whole collapsed strip is reclaimed (top drawn above the
        // parent origin) AND (b) the bottom stays exactly at the parent bottom.
        assertTrue(spec.contentOffsetY <= 0)
        assertEquals(1200, bottomWhenFilling(spec))
    }

    @Test
    fun `negative offset does not shrink the measure`() {
        val spec = collapseLayout(incomingMinHeight = 0, incomingMaxHeight = 900, collapseOffsetPx = -3)
        assertEquals(0, spec.measureMinHeight)
        assertEquals(900, spec.measureMaxHeight)
        assertEquals(0, spec.contentOffsetY)
        assertEquals(900, bottomWhenFilling(spec))
    }
}
