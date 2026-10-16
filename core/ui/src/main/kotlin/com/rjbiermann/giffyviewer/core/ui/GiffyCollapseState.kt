package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
 *  - A scroll UP (finger down) at any intermediate position re-expands
 *    continuously ("until scrolled up").
 *
 * The Scaffold shifts its content draw up by [offsetPx] (layout-time place, no
 * recomposition) so the viewport re-claims the collapsed strip.
 */
class GiffyCollapseState {
    var offsetPx by mutableIntStateOf(0)
        private set

    /** Total movable height (bar + extra rows). Set by GiffyScaffold measure. */
    internal var totalPx by mutableIntStateOf(0)

    /** Reset on source/tab changes (a fresh entry's grid starts at top). */
    fun reset() {
        offsetPx = 0
    }

    internal val connection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset =
                when {
                    available.y < 0 -> collapse(available)
                    // enterAlways semantics: a scroll-up expands the block
                    // FIRST — even mid-list — and lets the grid keep the rest.
                    available.y > 0 && offsetPx > 0 -> expand(available)
                    else -> Offset.Zero
                }
        }

    /** Finger-up/content-down: fold the header block. Returns consumed
     *  (same sign convention as the incoming delta). */
    private fun collapse(delta: Offset): Offset {
        if (totalPx <= 0) return Offset.Zero
        val before = offsetPx
        offsetPx = (offsetPx - delta.y.toInt()).coerceIn(0, totalPx)
        return Offset(0f, -(offsetPx - before).toFloat())
    }

    /** Scroll-up: unfold. Consumes at most the remaining offset. */
    private fun expand(delta: Offset): Offset {
        val before = offsetPx
        offsetPx = (offsetPx - delta.y.toInt()).coerceIn(0, totalPx)
        return Offset(0f, -(offsetPx - before).toFloat())
    }
}
