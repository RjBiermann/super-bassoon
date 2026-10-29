// PlayerView/AspectRatio resize modes are @UnstableApi — same file-level
// opt-in PlayerScreen.kt carries (lint UnsafeOptInUsageError).
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.player.GiffyPlayer
import com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory
import com.rjbiermann.giffyviewer.core.ui.AudioBadge
import com.rjbiermann.giffyviewer.core.ui.CreatorLabel
import com.rjbiermann.giffyviewer.core.ui.GiffyScaffold
import com.rjbiermann.giffyviewer.core.ui.LocalIsOnline
import com.rjbiermann.giffyviewer.core.ui.SCROLL_TO_TOP_THRESHOLD
import com.rjbiermann.giffyviewer.core.ui.ScrollToTopFab
import com.rjbiermann.giffyviewer.core.ui.avgColorOr
import com.rjbiermann.giffyviewer.core.ui.rememberGiffyCollapseState
import com.rjbiermann.giffyviewer.core.ui.rememberReducedMotion
import com.rjbiermann.giffyviewer.core.ui.rememberScrollingUp
import com.rjbiermann.giffyviewer.core.ui.scrollToTop
import com.rjbiermann.giffyviewer.core.ui.toTileContentScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * M3 (m3.material.io): Scaffold + small TopAppBar + FilterChip feed tabs +
 * linear refresh indicator. Masonry 2-col portrait / 3-col landscape (PLAN §9).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    gridColumns: Int = 2,
    onOpenPlayer: (Int) -> Unit = {},
    onOpenAccount: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenNiches: () -> Unit = {},
    onOpenFollowing: () -> Unit = {},
    onOpenCollections: () -> Unit = {},
    onOpenCustomFeeds: () -> Unit = {},
    /** Issue #13 §3.4: the grid's own feed hops (strip chips, creator chips,
     *  tile captions, a changed sort) are back-stack entries now — one route
     *  push per hop, BACK restores the previous feed + its scroll. The VM
     *  keeps no source history. */
    onFeedNav: (FeedSource) -> Unit = {},
    /** Inline feed autoplay (AGENTS-PLAYER spec): the 1-column feed's settled
     *  tile plays a muted+looped preview. Null factory = no inline preview. */
    playerFactory: GiffyPlayerFactory? = null,
    viewModel: FeedViewModel = hiltViewModel(),
    /** Issue #47 slice 1: niche-feed Join/Leave (the shared NicheFeedHeader's
     *  joinButton slot); Niche-scoped refresh + anonymous gate inside. */
    joinViewModel: NicheJoinViewModel = hiltViewModel(),
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle(false)
    val items = viewModel.gifs.collectAsLazyPagingItems()
    val context = LocalContext.current
    val reducedMotion = rememberReducedMotion()
    // Issue #33/#63: OS connectivity — the banner, the error-card classification
    // (offline outranks the breaker's synthetic 503) and the retry re-arm.
    val isOnline = LocalIsOnline.current
    // Issue #32: the WHOLE top section (title bar + tab strip + headers + chips
    // + filter row) folds on scroll-down and re-expands on scroll-up — owned by
    // GiffyCollapseState (M3's own behavior collapses only the bar; device-pass
    // 2026-10-16). Reduced motion → no state → static block.
    val collapsePx = rememberGiffyCollapseState()
    LaunchedEffect(source) { collapsePx.reset() }
    val feedAutoplay by viewModel.feedAutoplay.collectAsStateWithLifecycle(true)
    val dataSaver by viewModel.dataSaver.collectAsStateWithLifecycle(false)
    // Issue #28: the §5 video-fit pref governs tiles too — a tile's poster and
    // its inline preview surface match what the player shows on tap-through.
    val videoFit by viewModel.videoFit.collectAsStateWithLifecycle("fit")
    val showBlockHint by viewModel.showBlockHint.collectAsStateWithLifecycle()
    // B3: global §6 orientation filter — fallback for the per-feed pref in the
    // filtered-empty predicate (same merge as FeedPagingSource's read chain).
    val globalOrientation by viewModel.orientationFilter.collectAsStateWithLifecycle("any")
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // Issue #13: system back is owned by the NavHost — the one-level hop-back
    // machinery this file used to carry (BackHandler + canGoBack) is gone; a
    // hop is a route push and BACK pops it with per-entry scroll state.

    LaunchedEffect(showBlockHint) {
        if (showBlockHint) {
            snackbarHostState.showSnackbar("Tip: long-press a tile to block creators, tags or keywords")
            viewModel.dismissBlockHint()
        }
    }
    // Issue #47 slice 1: honest join/leave failure (the header state never
    // fakes success) — surfaced on the existing snackbar.
    val joinError by joinViewModel.error.collectAsStateWithLifecycle()
    LaunchedEffect(joinError) {
        joinError?.let {
            snackbarHostState.showSnackbar(it)
            joinViewModel.dismissError()
        }
    }
    // Screen-scope per-feed prefs (#31 chip badge + #28/§8 predicates read it
    // in BOTH the collapsing header row and the empty-state checks).
    val feedPrefs by remember(source.baseKey) { viewModel.feedPrefs(source.baseKey) }
        .collectAsStateWithLifecycle(
            com.rjbiermann.giffyviewer.core.datastore
                .FeedPrefs(),
        )

    GiffyScaffold(
        title = source.title(),
        onBack = null,
        modifier = modifier.fillMaxSize(),
        scrollBehavior = null,
        collapseState = if (reducedMotion) null else collapsePx,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Filled.Search, contentDescription = "search")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "settings")
            }
        },
        // Issue #32 (mobile): the feed-local header cluster collapses WITH the
        // top bar — scroll-down slides it under the bar (GiffyScaffold clips +
        // translates the topBar column); scroll-up re-reveals it and the
        // scroll-to-top Fab resets the offset. Reduced motion: the pinned
        // behavior never moves the offset, so the block is static.
        collapsingHeaderExtra = {
            // #19: the tab strip moved VERBATIM into the shared FeedTabStrip —
            // one call, same chips/More menu/selected logic; onOpen stays the
            // in-place viewModel.open; ALL screen seams non-null = mobile
            // zero-delta. State inputs arrive as plain parameters (collected
            // here, exactly the flows the inline block collected).
            val pinnedEntries by viewModel.pinnedNiches.collectAsStateWithLifecycle(emptySet())
            val favoritedFeeds by viewModel.favoritedFeeds.collectAsStateWithLifecycle(emptyList())
            val pinnedCreators by viewModel.pinnedCreators.collectAsStateWithLifecycle(emptySet())
            val customFeeds by viewModel.customFeeds.collectAsStateWithLifecycle(emptyList())
            FeedTabStrip(
                source = source,
                isLoggedIn = isLoggedIn,
                pinnedNiches = pinnedEntries,
                favoritedFeeds = favoritedFeeds,
                pinnedCreators = pinnedCreators,
                customFeeds = customFeeds,
                onOpen = onFeedNav,
                lastGroupSub = viewModel.lastGroupSub(),
                onOpenFollowing = onOpenFollowing,
                onOpenNiches = onOpenNiches,
                onOpenCollections = onOpenCollections,
                onOpenCustomFeeds = onOpenCustomFeeds,
            )

            // Feed identity headers (§7/§9 + issue #31): creator avatar/bio +
            // niche description/counts; refreshed per source, cleared for any
            // other source. The creator COUNTS row below stays as-is.
            LaunchedEffect(source) {
                viewModel.refreshCreatorStats((source as? FeedSource.Creator)?.username)
                viewModel.refreshNicheStats(nicheSourceId(source))
                // Join/Leave (issue #47 slice 1): refresh membership on a
                // niche entry — GET /v2/niches/following, 1 request per open.
                if (source is FeedSource.Niche) joinViewModel.refresh()
            }
            if (source is FeedSource.Creator) {
                viewModel.creatorStats.collectAsStateWithLifecycle().value?.let { stats ->
                    CreatorFeedHeader(stats)
                    // Existing counts row (§7/§9): v1/users counts above a
                    // creator's tiles — unchanged, not duplicated.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                "%,d posts · %,d followers · %,d views".format(
                                    java.util.Locale.US,
                                    stats.gifs,
                                    stats.followers,
                                    stats.views,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (source is FeedSource.Niche) {
                viewModel.nicheStats.collectAsStateWithLifecycle().value?.let { detail ->
                    val joined by joinViewModel.joined.collectAsStateWithLifecycle(initialValue = emptySet())
                    val pending by joinViewModel.pending.collectAsStateWithLifecycle(initialValue = emptySet())
                    NicheFeedHeader(
                        niche = detail,
                        // Site-parity Join/Leave beside the counts (logged-in
                        // only — anonymous writes would 401; NicheJoinViewModel
                        // flips state only on a verified 202 write).
                        joinButton =
                            if (joinViewModel.loggedIn) {
                                {
                                    Button(
                                        onClick = { joinViewModel.toggle(detail.id) },
                                        enabled = detail.id !in pending,
                                    ) {
                                        if (detail.id in pending) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.padding(end = 8.dp).size(14.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        }
                                        Text(joinNicheLabel(joined = detail.id in joined))
                                    }
                                }
                            } else {
                                null
                            },
                    )
                }
            }

            // For You scope selector (§7 Creators·Niches·All — logged-in only).
            if (source is FeedSource.ForYou && isLoggedIn) {
                val activeScope by viewModel.forYouScope.collectAsStateWithLifecycle("all")
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("All" to "all", "Creators" to "creators", "Niches" to "niches").forEach { (label, value) ->
                        FilterChip(
                            selected = activeScope == value,
                            onClick = { viewModel.setForYouScope(value) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // One sort+filter entry (§8, #18): the sort chips row is gone — the
            // dialog carries sort AND filters AND "Clear filters". Badge
            // semantics ride the tint (chips' selected state): a saved non-default
            // sort or an active filter lights the icon.
            var showFilter by remember { mutableStateOf(false) }
            val filtersActive =
                listOf(feedPrefs.duration, feedPrefs.resolution, feedPrefs.orientation).any { it.isNotEmpty() }
            val savedSort by remember(source.baseKey) { viewModel.sortFor(source.baseKey) }
                .collectAsStateWithLifecycle("")
            // "" = no chip chosen: the saved sort, else the order the surface
            // actually fetches with (creator = latest, #65) — the chip the
            // default view serves must read as selected.
            val activeSort = source.activeSort.ifEmpty { savedSort.ifEmpty { source.defaultSort } }
            // The badge lights for a NON-default sort: activeSort now includes
            // the surface default, which is the un-chosen state.
            val nonDefaultSort = activeSort.isNotEmpty() && activeSort != source.defaultSort
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showFilter = true }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort & filter",
                        tint =
                            if (filtersActive || nonDefaultSort) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
            // §8 strict-tags chip: tag-bundle custom feeds only (the merged
            // groups — all refs are tag refs).
            val tagBundle =
                (source as? FeedSource.Custom)?.let { c ->
                    c.refs.isNotEmpty() && c.refs.all { !it.startsWith("creator:") && !it.startsWith("niche:") }
                } == true
            if (showFilter) {
                FeedFilterDialog(
                    isTagBundle = tagBundle,
                    prefs = feedPrefs,
                    sortOptions = source.sortOptions(),
                    selectedSort = activeSort,
                    onApply = { next, sort ->
                        viewModel.setFeedPrefs(source.baseKey, next)
                        // Sort persistence rides the EXISTING setSort path
                        // (pendingForce one-shot bypass + adoption on open).
                        viewModel.setSort(source, sort)
                        // Issue #13 §3.4: a CHANGED sort is its own back-stack
                        // entry — BACK returns to the pre-sort feed, at its
                        // scroll position (the sort simply re-identifies the
                        // feed, so no in-place reset exists anymore).
                        if (sort != activeSort) onFeedNav(source.withSort(sort))
                        showFilter = false
                    },
                    onDismiss = { showFilter = false },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Matching creators above search results (§7) — tap opens their
            // feed. NOT part of the collapsing header (issue #32 scope): the
            // suggestions stay pinned the way the old layout stacked them.
            val creators by viewModel.creatorResults.collectAsStateWithLifecycle(emptyList())
            if (creators.isNotEmpty()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    creators.forEach { creator ->
                        SuggestionChip(
                            onClick = { onFeedNav(FeedSource.Creator(username = creator.username)) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CreatorLabel(creator.username, creator.verified)
                                    Text(" · ${creator.followers}⇡")
                                }
                            },
                        )
                    }
                }
            }

            // M3 linear indicator for refresh / append activity. FIXED 4dp slot —
            // show/hide must not shift the grid (same rule as the player's
            // progress chrome; user report: the indicator caused UI drift).
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible =
                        items.loadState.refresh is LoadState.Loading ||
                            items.loadState.append is LoadState.Loading,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            val refreshError = items.loadState.refresh as? LoadState.Error
            // Issue #63: one honest kind per failure shape (breaker-open 503,
            // 401, 429 exhaustion, IO) instead of the single "is it an
            // IOException" boolean that filed unrelated causes as offline.
            val errorKind = feedErrorKind(refreshError?.error, isOnline = isOnline)
            val errorCard = feedErrorCard(errorKind)
            // Issue #63: this card was the only error surface with no automatic
            // recovery — a breaker cooldown ending, the network returning or the
            // upstream healing all left it latched until the user tapped. Same
            // budget/shape as the niches page (≤2 debounced retries, re-armed by
            // a new failure or the connectivity flip).
            AutoRetryEffect(
                // Issue #63 follow-up: a sign-in-required failure can never be
                // retried into success — the card opens Account instead.
                error =
                    refreshError != null &&
                        items.itemCount == 0 &&
                        errorKind != FeedErrorKind.SignInRequired,
                success = items.loadState.refresh is LoadState.NotLoading,
                key = items.loadState.refresh,
                isOnline = isOnline,
                retry = viewModel::forceRefresh,
            )
            // Offline fallback (mobile For-You default slice): the SEEDED For
            // You source (network-live, no Room cache) that fails its FIRST
            // refresh reverts to Trending so the cached grid renders — gate 3
            // wants content, not an error state. Only the initial refresh
            // (itemCount == 0); mid-feed page errors never re-route the user.
            if (refreshError != null && items.itemCount == 0) {
                // A2 (stale-error guard): the effect fires AFTER the source
                // change, so a latched error from the PREVIOUS pager can still
                // be observed here (A1's reset-emit clears it on the next
                // PagingData delivery). Re-read the CURRENT state at fire time
                // instead of trusting the composition-time snapshot — the VM
                // reverts only when the failing refresh still belongs to this
                // source generation.
                LaunchedEffect(source) {
                    viewModel.notifyRefreshError(
                        hasContent = false,
                        currentSourceError = {
                            items.loadState.refresh is LoadState.Error && items.itemCount == 0
                        },
                    )
                }
            }
            val verifiedOnlyPref by viewModel.verifiedOnly.collectAsStateWithLifecycle(false)
            if (items.itemCount == 0 && refreshError != null) {
                // Issue #61: every retry affordance on this card was dead —
                // EmptyState had no click handling and PullToRefreshBox only
                // composed in the grid branch. Wire both to forceRefresh()
                // (generation bump → full pager restart with TTL bypass).
                // Same-tab re-tap stays a no-op — the copy no longer promises it.
                // Issue #63: a durable 401 (For You / Liked without a user token)
                // can never be retried into success — that card opens Account
                // instead; pull-down stays a plain retry.
                val signInRequired = errorKind == FeedErrorKind.SignInRequired
                val cardClick: () -> Unit = if (signInRequired) onOpenAccount else viewModel::forceRefresh
                PullToRefreshBox(
                    isRefreshing = items.loadState.refresh is LoadState.Loading,
                    onRefresh = { viewModel.forceRefresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    com.rjbiermann.giffyviewer.core.ui.EmptyState(
                        message = errorCard.message,
                        hint = errorCard.hint,
                        modifier = Modifier.fillMaxSize(),
                        onClick = cardClick,
                        onClickLabel = if (signInRequired) "Sign in" else "Retry",
                    )
                }
            } else if (items.itemCount == 0 &&
                items.loadState.refresh is LoadState.NotLoading &&
                (
                    listOf(feedPrefs.duration, feedPrefs.resolution).any { it.isNotEmpty() } ||
                        // B3: merge per-feed over the GLOBAL §6 orientation —
                        // same semantics as FeedPagingSource's read chain.
                        feedPrefs.orientation.ifEmpty { globalOrientation } != "any" ||
                        feedPrefs.untaggedOnly ||
                        verifiedOnlyPref
                )
            ) {
                // Strict client filter (lt10 etc.) can legitimately match zero
                // tiles — trending's server pool is ~100 items (live-proven:
                // page 3×40 → 20, page 4 → HTTP 400). Never a blank screen.
                com.rjbiermann.giffyviewer.core.ui.EmptyState(
                    message = "No videos match this filter",
                    hint = "Clear or loosen the filter chips",
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (items.itemCount == 0 &&
                items.loadState.refresh is LoadState.NotLoading &&
                (
                    source is FeedSource.Favorites ||
                        source is FeedSource.ForYou ||
                        source is FeedSource.Custom
                )
            ) {
                // UX: helpful empty state, never a blank screen (shared EmptyState).
                val msg =
                    when {
                        source is FeedSource.ForYou -> "Your For You feed is empty"
                        source is FeedSource.Custom -> "This feed is empty"
                        else -> "No favorites yet"
                    }
                val hint =
                    when {
                        source is FeedSource.ForYou -> "Follow creators and join niches to fill it"
                        // Audit fix: on an EMPTY feed there are no tiles to long-press —
                        // point at the player's overflow sheet instead.
                        source is FeedSource.Custom ->
                            "long-press a tile or use ⋯ in the player → “Add to custom feed…”"
                        else -> "open any video and use ⋯ → “Favorite @creator”"
                    }
                // Issue #30: the genuinely-empty copy stays; one manual-refresh
                // hint rides under it (the grid owns the pull-to-refresh area
                // itself — empty and reachable).
                Box(modifier = Modifier.fillMaxSize()) {
                    com.rjbiermann.giffyviewer.core.ui.EmptyState(
                        message = msg,
                        hint = hint,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    Text(
                        "Pull down to refresh",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                    )
                }
            } else {
                // Pull-to-refresh owns revalidate (same path as the TTL
                // revalidate); the scroll-up icon is NAVIGATION only (#23 — no
                // network action on click, one floating control over media).
                // Issue #13: scroll state is saveable and scoped to the nav
                // entry (back into a covered entry restores it). A CHANGED
                // sort is a DIFFERENT entry now (new route), so it still starts
                // at the top — the old keyBase-keyed reset is superseded.
                val gridState = rememberLazyStaggeredGridState()
                val showPill by rememberScrollingUp(gridState, SCROLL_TO_TOP_THRESHOLD)
                // Inline feed autoplay (AGENTS-PLAYER spec; live-parity verified
                // 2026-10-02 — the site's own 1-col feed runs ONE shared muted+
                // looped <video> on the settled tile): one shared player, first
                // tile ≥50% visible after a 150ms settle, paused on scroll/
                // off-view. Data-saver forces static posters; NO watch-history
                // infrastructure remains (feature removed, 2026-10 stream); 1
                // stream fetch per settled tile (rate-limit invariant).
                val autoplayOn = gridColumns == 1 && feedAutoplay && !dataSaver
                // Inline player ownership (issue #9, AGENTS-PLAYER): the shared
                // preview player is VM-owned — adopt a parked one (from FeedScreen
                // leave or PlayerScreen exit) so the settled tile continues the
                // SAME warm instance; a fresh one is built when nothing is parked.
                // The player type here is platform-owned, never saveable: process
                // death legitimately drops it (fresh build on return).
                val inlinePlayer =
                    if (autoplayOn && playerFactory != null) {
                        viewModel.recallInlinePlayer() ?: playerFactory.create(context).also(viewModel::returnInlinePlayer)
                    } else {
                        null
                    }
                DisposableEffect(inlinePlayer) {
                    if (inlinePlayer == null) {
                        // Autoplay off / non-1-col: nothing may park in the VM
                        // (the previous DisposableEffect released on this condition).
                        viewModel.dropInlinePlayer()
                    }
                    onDispose {
                        // Pass-through to the VM (issue #9): leaving composition
                        // parks (not releases) — PlayerScreen adopts immediately.
                        viewModel.returnInlinePlayer(inlinePlayer)
                    }
                }
                var inlineIndex by remember(source.keyBase) { mutableStateOf<Int?>(null) }
                if (inlinePlayer != null) {
                    // A4 (S3/H2): a source swap must not leave the shared inline
                    // player carrying the previous tab's gif — stop it before the
                    // fresh settle logic attaches, and drop the settled index
                    // (fresh key above; the stale-frame logic below re-paints).
                    LaunchedEffect(source.keyBase) {
                        inlinePlayer.stop()
                        inlineIndex = null
                    }
                    LaunchedEffect(inlinePlayer, gridState, items.itemCount) {
                        inlinePlayer.volume = 0f // muted preview, tap = full player
                        inlinePlayer.repeatMode = Player.REPEAT_MODE_ONE
                        snapshotFlow {
                            // The scroll-stop TRANSITION is part of the key: mid-fling
                            // the settle fires once and used to be skipped on
                            // isScrollInProgress (never retried → the shared player
                            // stayed paused on the previous gif's frozen frame).
                            val info = gridState.layoutInfo
                            val settled =
                                info.visibleItemsInfo
                                    .firstOrNull {
                                        isSettled(it.offset.y, it.size.height, info.viewportSize.height)
                                    }?.index
                            settled to gridState.isScrollInProgress
                        }.distinctUntilChanged().collect { (settled, scrolling) ->
                            if (scrolling) {
                                // Scroll in progress: pause (frozen frame stays on
                                // the current tile — standard video-list UX) and
                                // DON'T move the surface; the settle emission after
                                // the scroll-stop transition drives everything else.
                                inlinePlayer.pause()
                                return@collect
                            }
                            // Switching content: clear the previous gif's last
                            // frame BEFORE the surface moves to the new tile —
                            // otherwise the incoming tile paints the stale frame
                            // during the (re)attach window (reported glitch).
                            val nextGif = settled?.let { items[it] }
                            if (nextGif != null && inlinePlayer.currentGifId.value != nextGif.id) {
                                inlinePlayer.stop()
                            }
                            inlineIndex = settled
                            if (settled == null) return@collect
                            delay(INLINE_SETTLE_MS) // settle grace — skip hover-bys
                            if (gridState.isScrollInProgress) return@collect // re-checked after grace
                            val gif = items[settled] ?: return@collect
                            if (inlinePlayer.currentGifId.value != gif.id) {
                                inlinePlayer.playGif(gif, dataSaver = false)
                            } else if (!inlinePlayer.isPlaying) {
                                // Same page re-settled (scroll-start/stop flip or
                                // touch): just resume, never restart from 0.
                                inlinePlayer.play()
                            }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    PullToRefreshBox(
                        isRefreshing = items.loadState.refresh is LoadState.Loading,
                        onRefresh = { viewModel.forceRefresh() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyVerticalStaggeredGrid(
                            state = gridState,
                            columns = StaggeredGridCells.Fixed(gridColumns),
                            modifier = Modifier.fillMaxSize(),
                            // Issue #40: GiffyScaffold's content padding ALREADY
                            // carries the system nav-bar inset — a navBar-sized
                            // trailing padding here doubled it to the blank band
                            // below the feed while scrolled. 8dp is the grid's own
                            // gutter; the single inset strip stays the app standard.
                            contentPadding =
                                PaddingValues(
                                    start = 8.dp,
                                    end = 8.dp,
                                    top = 8.dp,
                                    bottom = 8.dp,
                                ),
                            verticalItemSpacing = 8.dp,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                                items[index]?.let { gif ->
                                    var sheetFor by remember { mutableStateOf<Gif?>(null) }
                                    GifTile(
                                        gif = gif,
                                        onClick = { onOpenPlayer(index) },
                                        onLongPress = { sheetFor = gif },
                                        onOpenCreator = {
                                            onFeedNav(FeedSource.Creator(username = gif.userName))
                                        },
                                        inlinePlayer = inlinePlayer,
                                        inlineAttached = inlineIndex == index,
                                        videoFit = videoFit,
                                    )
                                    if (sheetFor != null) {
                                        QuickBlockSheet(
                                            gif = gif,
                                            onDismiss = { sheetFor = null },
                                            viewModel = viewModel,
                                            addableFeedRef =
                                                (source as? FeedSource.Niche)?.let { packNicheRef(it.id, it.name, prefixed = true) },
                                            // Issue #13: the sheet's creator row is a route push.
                                            onOpenFeed = onFeedNav,
                                        )
                                    }
                                }
                            }
                        }
                        ScrollToTopFab(
                            visible = showPill,
                            onClick = {
                                scope.launch {
                                    gridState.scrollToTop(reducedMotion)
                                    // Issue #32: "top" includes the collapsed
                                    // header — re-expand the block so a
                                    // scroll-0 feed never keeps hidden rows.
                                    collapsePx.reset()
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }
}

/** Inline-preview settle grace before playback starts (AGENTS-PLAYER spec
 *  calibration knob — the site's threshold observed at ~a frame; 150ms skips
 *  hover-bys without feeling laggy). */
private const val INLINE_SETTLE_MS = 150L

/** Inline autoplay settle test (pure, unit-tested): an item counts when ≥50%
 *  of its main-axis height is inside the viewport. */
internal fun isSettled(
    offset: Int,
    size: Int,
    viewport: Int,
): Boolean {
    if (size <= 0) return false
    val visible = minOf(offset + size, viewport) - maxOf(offset, 0)
    return visible * 2 >= size
}

@Composable
private fun GifTile(
    gif: Gif,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    /** Links audit #1: tile caption @user → creator feed (in-place nav). */
    onOpenCreator: () -> Unit = {},
    /** Inline feed autoplay (AGENTS-PLAYER spec): shared muted preview player. */
    inlinePlayer: GiffyPlayer? = null,
    /** This tile is the settled one — hosts the shared player's surface. */
    inlineAttached: Boolean = false,
    /** §5 video fit for this tile's poster + inline surface (issue #28). */
    videoFit: String = "fit",
) {
    Column(
        modifier =
            Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress,
                    onLongClickLabel = "Open quick actions",
                ),
    ) {
        // avgColor placeholder + reserved aspect ratio: no layout jump while loading,
        // no black flash on slow cells (UX rule: reserve space for images).
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (gif.width > 0 && gif.height > 0) gif.width.toFloat() / gif.height else 0.8f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(avgColorOr(gif.avgColor, MaterialTheme.colorScheme.surfaceVariant)),
        ) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(gif.posterUrl)
                        .crossfade(200)
                        .build(),
                // Decorative: the visible "@user" Text announces the creator;
                // a duplicated contentDescription read both (audit 2026-10).
                contentDescription = null,
                // Issue #28: the poster follows the player's §5 video-fit pref.
                contentScale = videoFit.toTileContentScale(),
                modifier = Modifier.fillMaxSize(),
            )
            if (inlinePlayer != null && inlineAttached) {
                // Inline preview surface (AGENTS-PLAYER spec): the shared muted
                // player on the settled tile. TextureView surface (see
                // inline_player_view.xml): transparent until the first frame —
                // the poster underneath stays visible through attach/prepare,
                // no black window. PlayerView consumes no touches (controller
                // off) so the tile's tap/long-press keep working.
                AndroidView(
                    factory = { ctx ->
                        android.view.LayoutInflater
                            .from(ctx)
                            .inflate(
                                com.rjbiermann.giffyviewer.feature.feed.R.layout.inline_player_view,
                                null,
                                false,
                            ).apply {
                                isClickable = false
                                isFocusable = false
                            }
                    },
                    onRelease = { view ->
                        // Leak H1/H2 (OOM sweep 2026-10-16): the AndroidView is
                        // torn down on every settle-swap while the shared VM
                        // player outlives it — PlayerView only detaches its
                        // ComponentListener + TextureView surface via
                        // setPlayer(null), so each detached tile anchors a whole
                        // subtree to the player's listener set (monotone Java-heap
                        // growth). The update{} path's setPlayer(B) detach of A
                        // is handled by PlayerView itself.
                        (view as PlayerView).player = null
                    },
                    update = { view ->
                        (view as PlayerView).apply {
                            player = inlinePlayer
                            // Issue #28 (same mapping as PlayerScreen's
                            // videoFitMode): the inline surface matches the
                            // tile's poster scale under the same §5 pref.
                            resizeMode =
                                if (videoFit == "crop") {
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                } else {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                        }
                    },
                    modifier = Modifier.matchParentSize(),
                )
            }
            // Audio-know-before-tap (PLAN §307): hasAudio badge on tiles.
            // Vector icon, not an emoji glyph (skill rule: emoji-as-icons is
            // an anti-pattern; consistent with the player rail's Sound icon).
            if (gif.hasAudio) {
                AudioBadge(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        CreatorLabel(
            gif.userName,
            gif.verified,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            // Issue #51: labelSmall caption was ≈20dp tall — a nested clickable
            // inside the tile's combinedClickable. minimumInteractiveComponentSize
            // (before CreatorLabel's own clickable) grows the hit area to 48dp,
            // keeping the visual caption size.
            modifier = Modifier.padding(start = 4.dp).minimumInteractiveComponentSize(),
            onClick = onOpenCreator,
        )
    }
}
