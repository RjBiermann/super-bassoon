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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
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
import com.rjbiermann.giffyviewer.core.ui.SCROLL_TO_TOP_THRESHOLD
import com.rjbiermann.giffyviewer.core.ui.ScrollToTopFab
import com.rjbiermann.giffyviewer.core.ui.avgColorOr
import com.rjbiermann.giffyviewer.core.ui.rememberReducedMotion
import com.rjbiermann.giffyviewer.core.ui.rememberScrollingUp
import com.rjbiermann.giffyviewer.core.ui.scrollToTop
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
    /** Inline feed autoplay (AGENTS-PLAYER spec): the 1-column feed's settled
     *  tile plays a muted+looped preview. Null factory = no inline preview. */
    playerFactory: GiffyPlayerFactory? = null,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle(false)
    val items = viewModel.gifs.collectAsLazyPagingItems()
    val context = LocalContext.current
    // Content-first top bar (user report: the title/actions stayed static while
    // scrolling): M3 enterAlways — the bar collapses on scroll-down, re-reveals
    // on scroll-up, wired to the staggered grid via nestedScroll. The behavior
    // flattens to a static bar when motion is scaled to 0 (reduced-motion rule,
    // same pattern as the ScrollToTopFab scroll-to-top).
    val reducedMotion = rememberReducedMotion()
    val scrollBehavior =
        if (reducedMotion) {
            TopAppBarDefaults.pinnedScrollBehavior()
        } else {
            TopAppBarDefaults.enterAlwaysScrollBehavior()
        }
    val gridNestedScroll = scrollBehavior.nestedScrollConnection
    val feedAutoplay by viewModel.feedAutoplay.collectAsStateWithLifecycle(true)
    val dataSaver by viewModel.dataSaver.collectAsStateWithLifecycle(false)
    val showBlockHint by viewModel.showBlockHint.collectAsStateWithLifecycle()
    // B3: global §6 orientation filter — fallback for the per-feed pref in the
    // filtered-empty predicate (same merge as FeedPagingSource's read chain).
    val globalOrientation by viewModel.orientationFilter.collectAsStateWithLifecycle("any")
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // System back returns to the prior feed before exiting (round-3 audit:
    // chip/More/search-opened feeds set no overlay flag — back killed the app).
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    androidx.activity.compose.BackHandler(enabled = canGoBack) { viewModel.back() }

    LaunchedEffect(showBlockHint) {
        if (showBlockHint) {
            snackbarHostState.showSnackbar("Tip: long-press a tile to block creators, tags or keywords")
            viewModel.dismissBlockHint()
        }
    }

    GiffyScaffold(
        title = source.title(),
        onBack = null,
        modifier = modifier.fillMaxSize(),
        scrollBehavior = scrollBehavior,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Filled.Search, contentDescription = "search")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "settings")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                onOpen = { viewModel.open(it) },
                lastGroupSub = viewModel.lastGroupSub(),
                onOpenFollowing = onOpenFollowing,
                onOpenNiches = onOpenNiches,
                onOpenCollections = onOpenCollections,
                onOpenCustomFeeds = onOpenCustomFeeds,
            )

            // Matching creators above search results (§7) — tap opens their feed.
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
                            onClick = { viewModel.open(FeedSource.Creator(username = creator.username)) },
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

            // Creator stats header (§7/§9): v1/users counts above a creator's tiles.
            LaunchedEffect(source) {
                viewModel.refreshCreatorStats((source as? FeedSource.Creator)?.username)
            }
            if (source is FeedSource.Creator) {
                viewModel.creatorStats.collectAsStateWithLifecycle().value?.let { stats ->
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

            // For You scope selector (§7 Creators·Niches·All — logged-in only).
            if (source is FeedSource.ForYou && isLoggedIn) {
                val scope by viewModel.forYouScope.collectAsStateWithLifecycle("all")
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
                            selected = scope == value,
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
            val feedPrefs by remember(source.baseKey) { viewModel.feedPrefs(source.baseKey) }
                .collectAsStateWithLifecycle(
                    com.rjbiermann.giffyviewer.core.datastore
                        .FeedPrefs(),
                )
            val filtersActive =
                listOf(feedPrefs.duration, feedPrefs.resolution, feedPrefs.orientation).any { it.isNotEmpty() }
            val savedSort by remember(source.baseKey) { viewModel.sortFor(source.baseKey) }
                .collectAsStateWithLifecycle("")
            val activeSort = source.activeSort.ifEmpty { savedSort }
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
                            if (filtersActive || activeSort.isNotEmpty()) {
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
                        showFilter = false
                    },
                    onDismiss = { showFilter = false },
                )
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
            // A3 (S1 fix, no probes): the copy was hardcoded "offline" for ANY
            // empty+error path — breaker-open synthetic 503 / rate-limit
            // exhaustion / plain HTTP failures read the same. An IOException is
            // the only connectivity-shaped signal; everything else gets a
            // "went wrong — retry" copy.
            val offlineShaped = refreshError?.error is java.io.IOException
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
                com.rjbiermann.giffyviewer.core.ui.EmptyState(
                    message = if (offlineShaped) "Nothing cached yet" else "Something went wrong",
                    hint =
                        if (offlineShaped) {
                            "You're offline — reconnect to load the feed."
                        } else {
                            "Tap to retry — pull down or tap the tab again."
                        },
                    modifier = Modifier.fillMaxSize(),
                )
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
                com.rjbiermann.giffyviewer.core.ui
                    .EmptyState(modifier = Modifier.fillMaxSize(), message = msg, hint = hint)
            } else {
                // Pull-to-refresh owns revalidate (same path as the TTL
                // revalidate); the scroll-up icon is NAVIGATION only (#23 — no
                // network action on click, one floating control over media).
                // Fresh state per feed/sort: a sort change starts at the top
                // (user report — the grid kept the old scroll offset).
                val gridState =
                    remember(source.keyBase) {
                        androidx.compose.foundation.lazy.staggeredgrid
                            .LazyStaggeredGridState()
                    }
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
                            modifier = Modifier.fillMaxSize().nestedScroll(gridNestedScroll),
                            // Tiles scroll under the system nav (edge-to-edge);
                            // the inset is a contentPadding, not dead space.
                            contentPadding =
                                PaddingValues(
                                    start = 8.dp,
                                    end = 8.dp,
                                    top = 8.dp,
                                    bottom = 8.dp + navBarDp(),
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
                                            viewModel.open(FeedSource.Creator(username = gif.userName))
                                        },
                                        inlinePlayer = inlinePlayer,
                                        inlineAttached = inlineIndex == index,
                                    )
                                    if (sheetFor != null) {
                                        QuickBlockSheet(
                                            gif = gif,
                                            onDismiss = { sheetFor = null },
                                            viewModel = viewModel,
                                            addableFeedRef =
                                                (source as? FeedSource.Niche)?.let { packNicheRef(it.id, it.name, prefixed = true) },
                                        )
                                    }
                                }
                            }
                        }
                        ScrollToTopFab(
                            visible = showPill,
                            onClick = {
                                scope.launch { gridState.scrollToTop(reducedMotion) }
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
                contentScale = ContentScale.Crop,
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
                    update = { view -> (view as PlayerView).player = inlinePlayer },
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
            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp),
            onClick = onOpenCreator,
        )
    }
}

/** System nav-bar inset as dp (density-based; the layout extension didn't resolve). */
@Composable
private fun navBarDp(): androidx.compose.ui.unit.Dp =
    with(androidx.compose.ui.platform.LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
