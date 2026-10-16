package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Scaffold + TopAppBar + back icon, one shared idiom (2026-10 round-3 audit:
 * the boilerplate rendered 10×; copy-drift like "back"/"Back" now lives here).
 * `onBack = null` hides the navigation icon (home surface). `backIcon`/
 * `backDescription` cover the Close variant (Settings).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiffyScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    backDescription: String = "back",
    /** M3 collapse-on-scroll (FeedScreen content-first hide; null = static bar,
     *  every other surface unchanged). Callers wire
     *  Modifier.nestedScroll(behavior.nestedScrollConnection) on the scrolling
     *  content. Caller-created (not remembered here) so the owning screen can
     *  read/observe the state — e.g. reduced-motion snap-vs-animate. */
    scrollBehavior: TopAppBarScrollBehavior? = null,
    /** Issue #32 (mobile FeedScreen): header rows rendered AFTER the bar that
     *  collapse WITH it — the topBar column clips itself and slides by the
     *  bar's heightOffset, so a scrolled-down feed pulls the extra under the
     *  bar. Zero extra when a caller passes nothing. Reduced-motion callers
     *  use the pinned behavior whose heightOffset never leaves 0, so the
     *  default translation is static there. Column-scope: the block shares
     *  layout/top-rise semantics with the bar (stacked, not rowed). */
    collapsingHeaderExtra: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = snackbarHost,
        topBar = {
            // Issue #32: clip + slide the whole topBar column by the bar's
            // heightOffset — the collapsingHeaderExtra rides the collapse the
            // bar itself already owns; at 0 (rest, or a pinned/reduced-motion
            // bar) nothing moves.
            Column(
                modifier =
                    Modifier
                        .clipToBounds()
                        .graphicsLayer { translationY = scrollBehavior?.state?.heightOffset ?: 0f },
            ) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(backIcon, contentDescription = backDescription)
                            }
                        }
                    },
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
                collapsingHeaderExtra()
            }
        },
        content = content,
    )
}
