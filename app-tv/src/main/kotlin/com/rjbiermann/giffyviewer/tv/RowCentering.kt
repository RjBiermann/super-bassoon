package com.rjbiermann.giffyviewer.tv

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * #14 §B — pure helper: the scrollToItem offset (px) that centers an item of
 * [itemWidthPx] in a viewport of [viewportPx] whose content area starts
 * [leadingPaddingPx] in; null = already centered (the [deadbandPx] deadband,
 * ~±48dp at call sites, so small focus steps don't jitter). Pixel-based —
 * callers resolve dp against their own density.
 */
internal fun centeredScrollOffset(
    viewportPx: Int,
    itemWidthPx: Int,
    leadingPaddingPx: Int,
    deadbandPx: Int,
): Int? {
    if (viewportPx <= 0 || itemWidthPx <= 0) return null
    val offset = (viewportPx - 2 * leadingPaddingPx - itemWidthPx) / 2
    return if (abs(offset) <= deadbandPx) null else offset
}

/** Focused index + instant-recenter wiring shared by FeedRow/CreatorRow. */
internal data class RowCenterer(
    val state: LazyListState,
    /** (item index, gained|lost focus) — tracks the row's focused card; a
     *  focus MOVE between cards fires loss/gain in either order, so the
     *  loss branch only clears its own index. */
    val onFocus: (index: Int, focused: Boolean) -> Unit,
)

/**
 * #14 §B wiring: one remembered list state + the focused-card index; on every
 * focus change (and again when a loading row settles) the focused card is
 * INSTANTLY re-centered (scrollToItem — no per-step animation lag on the
 * D-pad). Only the row the focus moved in reacts; vertical traversal never
 * recenters (leaving a row clears its own index via the loss branch).
 */
@Composable
internal fun rememberCenteringRow(loading: Boolean): RowCenterer {
    val state = rememberLazyListState()
    var focusedIndex by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    // Same 16dp as the rows' LazyRow contentPadding; deadband ~±48dp (doc).
    val leadingPaddingPx = remember(density) { with(density) { 16.dp.roundToPx() } }
    val deadbandPx = remember(density) { with(density) { 48.dp.roundToPx() } }
    LaunchedEffect(focusedIndex, loading) {
        val index = focusedIndex
        if (index == null || loading) return@LaunchedEffect
        val info = state.layoutInfo
        val target = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@LaunchedEffect
        centeredScrollOffset(info.viewportSize.width, target.size, leadingPaddingPx, deadbandPx)?.let {
            state.scrollToItem(index, it)
        }
    }
    return remember {
        RowCenterer(state) { index, focused ->
            if (focused || focusedIndex == index) focusedIndex = if (focused) index else null
        }
    }
}
