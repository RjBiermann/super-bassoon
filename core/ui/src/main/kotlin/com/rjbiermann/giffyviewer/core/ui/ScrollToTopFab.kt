package com.rjbiermann.giffyviewer.core.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Scroll-to-top affordance (PLAN §9 Refresh-feed slot, repurposed #23): an
 * ICON-ONLY button that jumps the host list back to the top. NO network
 * action — pull-to-refresh (mobile) / the Refresh control (TV) own
 * revalidation, and an icon keeps "never two stacked floating controls over
 * full-bleed media" safe. Visible only while the host passes [visible]
 * (scrolled past the threshold AND scrolling upward).
 */
@Composable
fun ScrollToTopFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            IconButton(onClick = onClick, modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Scroll to top")
            }
        }
    }
}

/** Fab appears after ~10 items scrolled (PLAN §9). One spelling, shared by
 *  every surfaced list host (FeedScreen + the secondary list surfaces). */
const val SCROLL_TO_TOP_THRESHOLD = 10

/**
 * Fab visibility derivation (§9): shown only when the user has scrolled past
 * [threshold] items AND is scrolling upward (no strobing on small scrolls;
 * hidden while idle at position or at top). Host owns the list state.
 * Staggered-grid overload for FeedScreen's masonry grid.
 */
@Composable
fun rememberScrollingUp(
    state: LazyStaggeredGridState,
    threshold: Int,
): State<Boolean> {
    var previousIndex by remember { mutableIntStateOf(state.firstVisibleItemIndex) }
    var previousOffset by remember { mutableIntStateOf(state.firstVisibleItemScrollOffset) }
    return remember {
        derivedStateOf {
            if (state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0) {
                false
            } else if (state.firstVisibleItemIndex != previousIndex) {
                val scrollingUp = state.firstVisibleItemIndex < previousIndex
                previousIndex = state.firstVisibleItemIndex
                previousOffset = state.firstVisibleItemScrollOffset
                scrollingUp && state.firstVisibleItemIndex >= threshold
            } else if (state.firstVisibleItemScrollOffset != previousOffset) {
                val scrollingUp = state.firstVisibleItemScrollOffset < previousOffset
                previousOffset = state.firstVisibleItemScrollOffset
                scrollingUp && state.firstVisibleItemIndex >= threshold
            } else {
                false
            }
        }
    }
}

/** LazyColumn overload — same derivation for the secondary list surfaces. */
@Composable
fun rememberScrollingUp(
    state: LazyListState,
    threshold: Int,
): State<Boolean> {
    var previousIndex by remember { mutableIntStateOf(state.firstVisibleItemIndex) }
    var previousOffset by remember { mutableIntStateOf(state.firstVisibleItemScrollOffset) }
    return remember {
        derivedStateOf {
            if (state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0) {
                false
            } else if (state.firstVisibleItemIndex != previousIndex) {
                val scrollingUp = state.firstVisibleItemIndex < previousIndex
                previousIndex = state.firstVisibleItemIndex
                previousOffset = state.firstVisibleItemScrollOffset
                scrollingUp && state.firstVisibleItemIndex >= threshold
            } else if (state.firstVisibleItemScrollOffset != previousOffset) {
                val scrollingUp = state.firstVisibleItemScrollOffset < previousOffset
                previousOffset = state.firstVisibleItemScrollOffset
                scrollingUp && state.firstVisibleItemIndex >= threshold
            } else {
                false
            }
        }
    }
}

/** Reduced-motion read (shared): the system animator scale at 0 = no motion. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Scroll-to-top honoring the reduced-motion rule: scale 0 → instant jump. */
suspend fun LazyStaggeredGridState.scrollToTop(reducedMotion: Boolean) {
    if (reducedMotion) scrollToItem(0) else animateScrollToItem(0)
}

/** LazyColumn twin of [scrollToTop]. */
suspend fun LazyListState.scrollToTop(reducedMotion: Boolean) {
    if (reducedMotion) scrollToItem(0) else animateScrollToItem(0)
}
