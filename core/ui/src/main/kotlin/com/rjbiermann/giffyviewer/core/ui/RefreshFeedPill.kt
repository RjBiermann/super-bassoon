package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * "Refresh feed" pill (PLAN §9 Refresh-feed bullet): one action = animated
 * scroll-to-top + force revalidate (Instagram new-posts semantics — never two
 * stacked floating buttons over full-bleed media). Visible only while the host
 * passes [visible] (scrolled past the threshold AND scrolling upward).
 */
@Composable
fun RefreshFeedPill(
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
            Button(onClick = onClick, modifier = Modifier.padding(16.dp)) {
                Text("Refresh feed")
            }
        }
    }
}

/**
 * Pill visibility derivation (§9): shown only when the user has scrolled past
 * [threshold] items AND is scrolling upward (no strobing on small scrolls;
 * hidden while idle at position or at top). Host owns the list state.
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
