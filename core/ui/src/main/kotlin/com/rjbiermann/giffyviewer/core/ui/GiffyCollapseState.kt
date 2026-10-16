package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Issue #32's owned collapse for a topBar column taller than the M3 bar
 * (tabs/filter rows fold WITH the bar). M3's own [TopAppBarScrollBehavior]
 * sizes its offset limit from the bar alone, and M3 1.4's bar self-translates
 * internally — extending it would double-move the bar and leave the extra rows
 * pinned (device-pass 2026-10-16). This owns the whole movement:
 *
 *  - [connection] is attached at [GiffyScaffold]'s Scaffold level; inner
 *    scrollables (grid, lists) consume first, the collapse absorbs only the
 *    deltas left at the edge — classic enterAlways from the child's frame.
 *  - [offsetPx] ∈ [0, topColumnHeight] — callers see it for re-arm scrolls.
 *  - [expanded] flips mid-collapse; a scroll UP (finger down) at any
 *    intermediate position re-expands continuously ("until scrolled up").
 *
 * The Scaffold shifts its content draw up by [offsetPx] (layout-time place, no
 * recomposition) so the viewport re-claims the collapsed strip.
 */
class GiffyCollapseState {
    var offsetPx by mutableIntStateOf(0)
        private set

    /** True once the user has pushed the block off its rest position. */
    var expanded by mutableStateOf(true)
        private set

    /** Total movable height (bar + extra rows). Set by GiffyScaffold measure. */
    internal var totalPx by mutableIntStateOf(0)

    /** Reset on source/tab changes (a fresh entry's grid starts at top). */
    fun reset() {
        offsetPx = 0
        expanded = true
    }

    internal val connection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset =
                when {
                    available.y < 0 -> scroll(-available.y)
                    else -> Offset.Zero
                }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset =
                when {
                    available.y > 0 -> scroll(available.y)
                    else -> Offset.Zero
                }
        }

    /** Positive = collapse by that many px; negative = expand. Returns consumed. */
    private fun scroll(by: Float): Offset {
        if (totalPx <= 0) return Offset.Zero
        val before = offsetPx
        offsetPx = (offsetPx + by.toInt()).coerceIn(0, totalPx)
        if (offsetPx != before) expanded = offsetPx == 0
        return Offset(0f, (offsetPx - before).toFloat() * if (by > 0) 1f else -1f)
    }
}
