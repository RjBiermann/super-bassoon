package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.rjbiermann.giffyviewer.core.ui.GiffyColors
import com.rjbiermann.giffyviewer.core.ui.RefreshFeedPill
import com.rjbiermann.giffyviewer.core.ui.rememberScrollingUp
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
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
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
                    IconButton(onClick = onOpenAccount) {
                        Icon(Icons.Outlined.Person, contentDescription = "account")
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
                // For You = server personalized feed, logged-in only (PLAN §7);
                // anonymous client-side blend is spec'd but unscheduled.
                val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle(false)
                listOf(
                    FeedSource.Trending,
                    FeedSource.ForYou,
                    FeedSource.Favorites,
                    FeedSource.TopThisWeek,
                ).filterNot { it is FeedSource.ForYou && !isLoggedIn }
                    .forEach { candidate ->
                        FilterChip(
                            selected = source == candidate,
                            onClick = { viewModel.open(candidate) },
                            label = { Text(candidate.title()) },
                        )
                    }

                // Explore = Top Creators surface (§9 lingo) — a screen, not a feed
                // source; never selected (same as Following/Niches/Groups chips).
                FilterChip(
                    selected = false,
                    onClick = onOpenExplore,
                    label = { Text("Explore") },
                )
                if (isLoggedIn) {
                    FilterChip(
                        selected = false,
                        onClick = onOpenFollowing,
                        label = { Text("Following…") },
                    )
                }

                FilterChip(
                    selected = source is FeedSource.Niche && source !in pinnedNiches,
                    onClick = onOpenNiches,
                    label = { Text("Niches…") },
                )
                FilterChip(
                    selected = false,
                    onClick = onOpenGroups,
                    label = { Text("Groups…") },
                )
                // pinned niches become tabs (PLAN §7 pin-to-tabs)
                pinnedNiches.forEach { niche ->
                    FilterChip(
                        selected = source == niche,
                        onClick = { viewModel.open(niche) },
                        label = { Text(niche.name) },
                    )
                }
                // FAVORITED groups become tabs (PLAN §7 groups)
                val favGroups by viewModel.favoriteGroups.collectAsStateWithLifecycle(emptyList())
                favGroups.filter { it.state == "FAVORITED" }.forEach { group ->
                    val groupFeed = FeedSource.Group(group.id, group.name, group.tagList.split(','))
                    FilterChip(
                        selected = source == groupFeed,
                        onClick = { viewModel.open(groupFeed) },
                        label = { Text(group.name) },
                    )
                }
                // pinned creators become tabs (PLAN §7 pin-to-tabs), shared with TV
                val pinnedCreators by viewModel.pinnedCreators.collectAsStateWithLifecycle(emptySet())
                pinnedCreators.forEach { username ->
                    val creatorFeed = FeedSource.Creator(username)
                    FilterChip(
                        selected = source == creatorFeed,
                        onClick = { viewModel.open(creatorFeed) },
                        label = { Text("@$username") },
                    )
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
                                Text("@${creator.username} · ${creator.followers}⇡")
                            },
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
            if (items.itemCount == 0 && refreshError) {
                OfflineNotice(modifier = Modifier.fillMaxSize())
            } else if (source is FeedSource.Favorites &&
                items.itemCount == 0 &&
                items.loadState.refresh is LoadState.NotLoading
            ) {
                // UX: helpful empty state, never a blank screen.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No favorites yet — long-press a tile and choose Favorite.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // §9 Refresh-feed: pull-to-refresh (same path as TTL revalidate) +
                // scroll-up "Refresh feed" pill = scroll-to-top + force revalidate.
                val gridState = rememberLazyStaggeredGridState()
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

/** Airplane-mode cold start: cached rows render; only an empty cache shows this. */
@Composable
private fun OfflineNotice(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nothing cached yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "You're offline — reconnect to load the feed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** PLAN §9 quick sheet: block creator / tags / keyword / don't block. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickBlockSheet(
    gif: Gif,
    onDismiss: () -> Unit,
    viewModel: FeedViewModel,
    showSpeed: Boolean = false,
    currentSpeed: Float = 1f,
    onSpeedChange: (Float) -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                "@${gif.userName}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (showSpeed) {
                // Continuous slider (0.25×–2×): the user asked for fine control
                // beyond preset chips; applies on release to avoid player thrash.
                var dragSpeed by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Speed " + String.format("%.2f", dragSpeed) + "×",
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (dragSpeed != currentSpeed) {
                                GiffyColors.Lime
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("0.25×", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = dragSpeed,
                            onValueChange = { dragSpeed = it },
                            valueRange = 0.25f..2f,
                            onValueChangeFinished = { onSpeedChange(dragSpeed) },
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        Text("2×", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val favState by viewModel
                .creatorState(gif.userName)
                .collectAsState(initial = null)
            listStyle(if (favState == "FAVORITED") "Unfavorite @${gif.userName}" else "Favorite @${gif.userName}") {
                viewModel.toggleFavoriteCreator(gif.userName)
                onDismiss()
            }
            val pinnedCreators by viewModel.pinnedCreators.collectAsState(initial = emptySet())
            listStyle(
                if (gif.userName.lowercase() in pinnedCreators) {
                    "Unpin @${gif.userName} from home"
                } else {
                    "Pin @${gif.userName} to home"
                },
            ) {
                viewModel.togglePinnedCreator(gif.userName)
                onDismiss()
            }
            listStyle(
                "Block creator",
            ) {
                viewModel.blockCreator(gif.userName)
                onDismiss()
            }
            gif.tags.take(3).forEach { tag ->
                val tagState by viewModel
                    .tagState(tag)
                    .collectAsState(initial = null)
                listStyle(
                    if (tagState == "FAVORITED") "Unfavorite tag “$tag”" else "Favorite tag “$tag”",
                ) {
                    viewModel.toggleFavoriteTag(tag)
                    onDismiss()
                }
                listStyle("Block tag “$tag”") {
                    viewModel.blockTag(tag)
                    onDismiss()
                }
            }
            listStyle("Block keyword “${gif.tags.firstOrNull() ?: gif.userName}”") {
                viewModel.blockKeyword(gif.tags.firstOrNull() ?: gif.userName)
                onDismiss()
            }
            listStyle("Don't block", onDismiss)
        }
    }
}

@Composable
private fun ColumnScope.listStyle(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

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
                contentDescription = "Gif by @${gif.userName}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = "@${gif.userName}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp),
        )
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
