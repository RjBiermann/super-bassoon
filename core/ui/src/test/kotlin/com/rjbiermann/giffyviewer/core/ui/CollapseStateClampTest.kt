package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** Offline banner/rotation/row-set changes re-measure the header: a shrunken
 *  total must clamp the stored offset, or the content stays drawn above the
 *  header by more than its height (blank band at the bottom). */
class CollapseStateClampTest {
    private fun collapsed(
        state: GiffyCollapseState,
        byPx: Float,
    ) {
        state.connection.onPreScroll(Offset(0f, -byPx), NestedScrollSource.UserInput)
    }

    @Test
    fun `shrinking the header clamps the offset`() {
        val state = GiffyCollapseState()
        state.totalPx = 300
        collapsed(state, 250f)
        assertEquals(250, state.offsetPx)

        state.totalPx = 120 // offline banner gone / rotation / row set changed
        assertEquals(120, state.offsetPx)
    }

    @Test
    fun `growing the header keeps the offset`() {
        val state = GiffyCollapseState()
        state.totalPx = 300
        collapsed(state, 100f)
        state.totalPx = 500
        assertEquals(100, state.offsetPx)
    }
}
