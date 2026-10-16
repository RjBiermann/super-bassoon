package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged

/**
 * Scaffold + TopAppBar + back icon, one shared idiom (2026-10 round-3 audit:
 * the boilerplate rendered 10×; copy-drift like "back"/"Back" now lives here).
 * `onBack = null` hides the navigation icon (home surface). `backIcon`/
 * `backDescription` cover the Close variant (Settings).
 *
 * Issue #32 collapse (mobile FeedScreen): when the caller passes
 * [collapsingHeaderExtra] + [collapseState], the extra rows fold WITH the bar
 * under an OWNED nested-scroll sweep (GiffyCollapseState) — M3's behavior
 * collapses only the bar and self-translates internally, which pinned the rows
 * and double-moved the title (device-pass 2026-10-16). The content draw shifts
 * up with the collapse so the viewport re-claims the strip; scrolling up
 * re-expands continuously ("until scrolled up"). Reduced-motion callers pass
 * no state → static block, exactly like every other surface.
 *
 * Issue #33: the offline banner rides the same topBar column (above the bar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiffyScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    backDescription: String = "back",
    /** M3 collapse-on-scroll (titles-only surfaces; null = static bar — every
     *  surface but FeedScreen). Callers wire
     *  Modifier.nestedScroll(behavior.nestedScrollConnection) on the scrolling
     *  content themselves if they build a behavior. */
    scrollBehavior: TopAppBarScrollBehavior? = null,
    /** Issue #31/#32: rows rendered UNDER the bar that collapse with it via
     *  [collapseState]. Zero extra when the caller passes nothing. */
    collapsingHeaderExtra: @Composable ColumnScope.() -> Unit = {},
    /** The owned collapse driver for the extra block. null → static rows. */
    collapseState: GiffyCollapseState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val useCollapse = collapseState != null
    Scaffold(
        // The collapse connection rides the Scaffold → every inner scrollable
        // (grid, lists) feeds it after consuming its own scroll: collapse only
        // when the child reaches its edge (scrolling down), expand on scroll-up.
        modifier =
            modifier.then(
                if (useCollapse) {
                    Modifier.nestedScroll(collapseState!!.connection)
                } else {
                    Modifier
                },
            ),
        snackbarHost = snackbarHost,
        topBar = {
            val topColumn =
                Modifier
                    .clipToBounds()
                    .then(
                        if (useCollapse) {
                            Modifier.graphicsLayer {
                                translationY = -collapseState!!.offsetPx.toFloat()
                            }
                        } else {
                            Modifier
                        },
                    )
            Column(
                modifier =
                    topColumn
                        .onSizeChanged { if (useCollapse) collapseState!!.totalPx = it.height },
            ) {
                // Issue #33: gated by the LocalIsOnline carrier (TV's Root
                // overlay owns its own read; TV shared screens keep the
                // default-true = no double banner).
                if (offlineBannerVisible(LocalIsOnline.current)) {
                    OfflineBanner()
                }
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
                    // A collapseState owns ALL movement — the bar must stay
                    // pinned internally or M3 double-translates the title.
                    scrollBehavior = if (useCollapse) null else scrollBehavior,
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
                collapsingHeaderExtra()
            }
        },
    ) { padding ->
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        if (useCollapse) {
            // Re-claim the collapsed strip: shift the content draw up by the
            // collapse offset (layout-time place — no recomposition; the grid
            // measures once). The topBar column already paints over this area.
            // 2026-10-16 jam fix: shifting alone left the content SHORT at
            // full collapse — its bottom floated H-offsetPx off the screen,
            // and drags landing in that uncovered strip hit no scrollable at
            // all (FeedScreen froze after one swipe). Measure the content
            // TALLER by the offset so it still reaches the screen bottom.
            val state = collapseState!!
            Box(
                modifier =
                    Modifier.layout { measurable, constraints ->
                        val extra = state.offsetPx
                        val placeable =
                            measurable.measure(
                                constraints.copy(
                                    minHeight = constraints.minHeight + extra,
                                    maxHeight = constraints.maxHeight + extra,
                                ),
                            )
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, -extra)
                        }
                    },
            ) {
                content(
                    PaddingValues(
                        start = padding.calculateStartPadding(layoutDirection),
                        top = padding.calculateTopPadding(),
                        end = padding.calculateEndPadding(layoutDirection),
                        bottom = padding.calculateBottomPadding(),
                    ),
                )
            }
        } else {
            content(padding)
        }
    }
}

/** Factory for the [GiffyCollapseState] a collapsing screen owns. */
@Composable
fun rememberGiffyCollapseState(): GiffyCollapseState = remember { GiffyCollapseState() }
