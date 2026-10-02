package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.ui.RefreshFeedPill
import com.rjbiermann.giffyviewer.core.ui.rememberScrollingUp
import kotlinx.coroutines.flow.map
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
    onOpenGroups: () -> Unit = {},
    onOpenExplore: () -> Unit = {},
    onOpenFollowing: () -> Unit = {},
    onOpenCollections: () -> Unit = {},
    onOpenCustomFeeds: () -> Unit = {},
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle(false)
    val items = viewModel.gifs.collectAsLazyPagingItems()
    val showBlockHint by viewModel.showBlockHint.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(showBlockHint) {
        if (showBlockHint) {
            snackbarHostState.showSnackbar("Tip: long-press a tile to block creators, tags or keywords")
            viewModel.dismissBlockHint()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(source.title()) },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "search")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "settings")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // M3 FilterChips — feed tabs (more feeds join in Phase 7: groups, For You, custom)
            // pinned niches become tabs (PLAN §7 pin-to-tabs): "id|name" entries
            val pinnedEntries by viewModel.pinnedNiches.collectAsStateWithLifecycle(emptySet())
            val pinnedNiches =
                remember(pinnedEntries) {
                    pinnedEntries.mapNotNull { entry ->
                        entry
                            .split('|', limit = 2)
                            .takeIf { it.size == 2 }
                            ?.let { (id, name) -> FeedSource.Niche(id, name) }
                    }
                }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // FIXED set only — three primary tabs + a More menu (consolidated;
                // pinned/custom grow unbounded and the row became unusable).
                // For You = server personalized feed, logged-in only (PLAN §7).
                // Site home order: For You first when logged in (verified sweep).
                listOf(
                    FeedSource.ForYou,
                    FeedSource.Trending,
                    FeedSource.Favorites,
                ).filterNot { it is FeedSource.ForYou && !isLoggedIn }
                    .forEach { candidate ->
                        FilterChip(
                            selected = source == candidate,
                            onClick = { viewModel.open(candidate) },
                            label = { Text(candidate.title()) },
                        )
                    }

                // More ▾ = every secondary surface, grouped in sections (M3
                // DropdownMenu): screens · custom feeds · pinned tabs. The open
                // surface keeps a selected marker when it lives here.
                var moreOpen by remember { mutableStateOf(false) }
                val favGroups by viewModel.favoriteGroups.collectAsStateWithLifecycle(emptyList())
                val pinnedCreators by viewModel.pinnedCreators.collectAsStateWithLifecycle(emptySet())
                val customFeeds by viewModel.customFeeds.collectAsStateWithLifecycle(emptyList())
                Box {
                    FilterChip(
                        selected =
                            source is FeedSource.TopThisWeek ||
                                source is FeedSource.Liked ||
                                source is FeedSource.Custom ||
                                (source is FeedSource.Niche) ||
                                source is FeedSource.Creator ||
                                source is FeedSource.Group,
                        onClick = { moreOpen = true },
                        label = { Text("More ▾") },
                    )
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Surprise me") },
                            onClick = {
                                moreOpen = false
                                viewModel.surpriseMe { ok ->
                                    if (!ok) {
                                        scope.launch { snackbarHostState.showSnackbar("Nothing cached yet") }
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Top This Week") },
                            onClick = {
                                moreOpen = false
                                viewModel.open(FeedSource.TopThisWeek)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Explore") },
                            onClick = {
                                moreOpen = false
                                onOpenExplore()
                            },
                        )
                        if (isLoggedIn) {
                            DropdownMenuItem(
                                text = { Text("Liked GIFs & Images") },
                                onClick = {
                                    moreOpen = false
                                    viewModel.open(FeedSource.Liked)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Following…") },
                                onClick = {
                                    moreOpen = false
                                    onOpenFollowing()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Collections…") },
                                onClick = {
                                    moreOpen = false
                                    onOpenCollections()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Niches…") },
                            onClick = {
                                moreOpen = false
                                onOpenNiches()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Groups…") },
                            onClick = {
                                moreOpen = false
                                onOpenGroups()
                            },
                        )
                        // Always reachable: the builder is the ONLY way to create
                        // a feed, so "New feed…" must not be gated on having any
                        // (fresh install would otherwise never reach the screen).
                        if (customFeeds.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                "Custom feeds",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            customFeeds.forEach { def ->
                                DropdownMenuItem(
                                    text = { Text(def.name) },
                                    onClick = {
                                        moreOpen = false
                                        viewModel.open(FeedSource.Custom(def.id, def.name, parseCustomRefs(def.sourcesJson)))
                                    },
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text(if (customFeeds.isEmpty()) "New custom feed…" else "New feed…") },
                            onClick = {
                                moreOpen = false
                                onOpenCustomFeeds()
                            },
                        )
                        val pinnedSection =
                            pinnedNiches.map { it as FeedSource } +
                                favGroups.filter { it.state == "FAVORITED" }.map {
                                    FeedSource.Group(
                                        it.id,
                                        it.name,
                                        it.tagList.split(','),
                                    ) as FeedSource
                                } +
                                pinnedCreators.map { FeedSource.Creator(it) as FeedSource }
                        if (pinnedSection.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                "Pinned",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            pinnedSection.forEach { feed ->
                                DropdownMenuItem(
                                    text = { Text(feed.title()) },
                                    onClick = {
                                        moreOpen = false
                                        viewModel.open(feed)
                                    },
                                )
                            }
                        }
                    }
                }
            }

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
                                    Text("@${creator.username} · ${creator.followers}⇡")
                                    if (creator.verified) {
                                        com.rjbiermann.giffyviewer.core.ui.VerifiedTick(
                                            modifier = Modifier.padding(start = 3.dp).size(14.dp),
                                        )
                                    }
                                }
                            },
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

            // Per-feed server sort chips (§8) — only verified orders surface.
            val sortOptions = source.sortOptions()
            if (sortOptions.isNotEmpty()) {
                val savedSort by remember(source.baseKey) { viewModel.sortFor(source.baseKey) }
                    .collectAsStateWithLifecycle("")
                val activeSort = source.activeSort.ifEmpty { savedSort }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sortOptions.forEach { (label, order) ->
                        FilterChip(
                            selected = activeSort == order,
                            onClick = { viewModel.setSort(source, order) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // §8 range chips (client-side per-feed filters): Filter ▾ entry →
            // dialog with duration / resolution / orientation chip groups.
            // Orientation "" follows the global §6 pref (Settings).
            var showFilter by remember { mutableStateOf(false) }
            val feedPrefs by remember(source.baseKey) { viewModel.feedPrefs(source.baseKey) }
                .collectAsStateWithLifecycle(
                    com.rjbiermann.giffyviewer.core.datastore
                        .FeedPrefs(),
                )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = listOf(feedPrefs.duration, feedPrefs.resolution, feedPrefs.orientation).any { it.isNotEmpty() },
                    onClick = { showFilter = true },
                    label = { Text("Filter ▾") },
                )
                if (listOf(feedPrefs.duration, feedPrefs.resolution, feedPrefs.orientation).any { it.isNotEmpty() }) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            viewModel.setFeedPrefs(
                                source.baseKey,
                                com.rjbiermann.giffyviewer.core.datastore
                                    .FeedPrefs(),
                            )
                        },
                        label = { Text("Clear") },
                    )
                }
            }
            if (showFilter) {
                FeedFilterDialog(
                    isGroup = source is FeedSource.Group,
                    prefs = feedPrefs,
                    onApply = { next ->
                        viewModel.setFeedPrefs(source.baseKey, next)
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

            val refreshError = items.loadState.refresh is LoadState.Error
            val verifiedOnlyPref by viewModel.verifiedOnly.collectAsStateWithLifecycle(false)
            if (items.itemCount == 0 && refreshError) {
                com.rjbiermann.giffyviewer.core.ui.EmptyState(
                    message = "Nothing cached yet",
                    hint = "You're offline — reconnect to load the feed.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (items.itemCount == 0 &&
                items.loadState.refresh is LoadState.NotLoading &&
                (
                    listOf(feedPrefs.duration, feedPrefs.resolution, feedPrefs.orientation).any { it.isNotEmpty() } ||
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
                (source is FeedSource.Favorites || source is FeedSource.ForYou || source is FeedSource.Custom)
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
                // §9 Refresh-feed: pull-to-refresh (same path as TTL revalidate) +
                // scroll-up "Refresh feed" pill = scroll-to-top + force revalidate.
                // Fresh state per feed/sort: a sort change starts at the top
                // (user report — the grid kept the old scroll offset).
                val gridState =
                    remember(source.keyBase) {
                        androidx.compose.foundation.lazy.staggeredgrid
                            .LazyStaggeredGridState()
                    }
                val showPill by rememberScrollingUp(gridState, SCROLL_PILL_THRESHOLD)
                val reducedMotion =
                    remember {
                        android.provider.Settings.Global.getFloat(
                            context.contentResolver,
                            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                            1f,
                        ) == 0f
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
                                    )
                                    if (sheetFor != null) {
                                        QuickBlockSheet(
                                            gif = gif,
                                            onDismiss = { sheetFor = null },
                                            viewModel = viewModel,
                                            addableFeedRef =
                                                (source as? FeedSource.Niche)?.let { "niche:${it.id}|${it.name}" },
                                        )
                                    }
                                }
                            }
                        }
                        // One action: scroll-to-top + force revalidate (PLAN §9).
                        RefreshFeedPill(
                            visible = showPill,
                            onClick = {
                                viewModel.forceRefresh()
                                scope.launch {
                                    if (reducedMotion) {
                                        gridState.scrollToItem(0)
                                    } else {
                                        gridState.animateScrollToItem(0)
                                    }
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

/** Pill appears after ~10 items scrolled (PLAN §9). */
private const val SCROLL_PILL_THRESHOLD = 10

@Composable
private fun GifTile(
    gif: Gif,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
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
                    .background(avgColorOr(gif, MaterialTheme.colorScheme.surfaceVariant)),
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
            // Audio-know-before-tap (PLAN §307): hasAudio badge on tiles.
            // Vector icon, not an emoji glyph (skill rule: emoji-as-icons is
            // an anti-pattern; consistent with the player rail's Sound icon).
            if (gif.hasAudio) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = "has sound",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp),
        ) {
            Text(
                text = "@${gif.userName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (gif.verified) {
                com.rjbiermann.giffyviewer.core.ui.VerifiedTick(
                    modifier = Modifier.padding(start = 3.dp).size(14.dp),
                )
            }
        }
    }
}

/** avgColor is "#rrggbb"; fall back to theme surface on anything unexpected. */
@Composable
private fun avgColorOr(
    gif: Gif,
    fallback: Color,
): Color =
    try {
        Color(android.graphics.Color.parseColor(gif.avgColor))
    } catch (_: IllegalArgumentException) {
        fallback
    }

/** System nav-bar inset as dp (density-based; the layout extension didn't resolve). */
@Composable
private fun navBarDp(): androidx.compose.ui.unit.Dp =
    with(androidx.compose.ui.platform.LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
