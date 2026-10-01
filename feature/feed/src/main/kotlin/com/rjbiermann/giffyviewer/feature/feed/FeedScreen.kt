package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.mutableStateListOf
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
                val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle(false)
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
                            DropdownMenuItem(
                                text = { Text("New feed…") },
                                onClick = {
                                    moreOpen = false
                                    onOpenCustomFeeds()
                                },
                            )
                        }
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
                                Text("@${creator.username} · ${creator.followers}⇡")
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
            if (items.itemCount == 0 && refreshError) {
                com.rjbiermann.giffyviewer.core.ui.EmptyState(
                    message = "Nothing cached yet",
                    hint = "You're offline — reconnect to load the feed.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (items.itemCount == 0 &&
                items.loadState.refresh is LoadState.NotLoading &&
                (source is FeedSource.Favorites || source is FeedSource.ForYou)
            ) {
                // UX: helpful empty state, never a blank screen (shared EmptyState).
                val msg =
                    when {
                        source is FeedSource.ForYou -> "Your For You feed is empty"
                        else -> "No favorites yet"
                    }
                val hint =
                    when {
                        source is FeedSource.ForYou -> "Follow creators and join niches to fill it"
                        else -> "long-press a tile and choose “Favorite @creator”"
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
    /** Surfaced when the OPEN feed itself is addable (e.g. a niche). */
    addableFeedRef: String? = null,
) {
    // Hoisted for the AddToCustomFeedDialog scope below.
    val customFeeds by viewModel.customFeeds.collectAsState(initial = emptyList())
    var showAddToFeed by remember { mutableStateOf(false) }
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
                        text = "Speed " + String.format(java.util.Locale.US, "%.2f", dragSpeed) + "×",
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
            // Two views instead of a flat flood (user: "too many options"):
            // main = creator + the rest; Tags… swaps to a tag submenu.
            var view by remember { mutableStateOf("main") }
            if (view == "main") {
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
                listStyle("Block creator") {
                    viewModel.blockCreator(gif.userName)
                    onDismiss()
                }
                // Quick "add to custom feed" (PLAN §7): one entry + picker dialog
                // (its checkboxes cover the tags too — no per-tag rows here).
                if (customFeeds.isNotEmpty()) {
                    listStyle("Add to custom feed…") { showAddToFeed = true }
                }
                if (gif.tags.isNotEmpty()) {
                    listStyle("Tags…") { view = "tags" }
                }
                listStyle("Block keyword “${gif.tags.firstOrNull() ?: gif.userName}”") {
                    viewModel.blockKeyword(gif.tags.firstOrNull() ?: gif.userName)
                    onDismiss()
                }
                listStyle("Close", onDismiss)
            } else {
                Text(
                    "Tags",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
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
                listStyle("‹ Back", { view = "main" })
            }
        }
        if (showAddToFeed) {
            AddToCustomFeedDialog(
                gif = gif,
                customFeeds = customFeeds,
                addableFeedRef = addableFeedRef,
                onAdd = { defId, ref ->
                    viewModel.addToCustomFeed(defId, ref)
                    onDismiss()
                },
                onDismiss = { showAddToFeed = false },
            )
        }
    }
}

/** Pick which custom feed + which refs (creator / tags) to add. */
@Composable
private fun AddToCustomFeedDialog(
    gif: Gif,
    customFeeds: List<com.rjbiermann.giffyviewer.core.database.CustomFeedEntity>,
    onAdd: (defId: Long, ref: String) -> Unit,
    onDismiss: () -> Unit,
    addableFeedRef: String? = null,
) {
    var feedId by remember { mutableStateOf(customFeeds.firstOrNull()?.id) }
    val creatorRef = "creator:${gif.userName.lowercase().trim()}"
    val tagRefs = gif.tags.take(3).map { "tag:${it.lowercase().trim()}" }
    val nicheRef = addableFeedRef
    val selected = remember { mutableStateListOf(creatorRef) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to custom feed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                customFeeds.forEach { def ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { feedId = def.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = feedId == def.id,
                            onClick = { feedId = def.id },
                        )
                        Text(def.name)
                    }
                }
                HorizontalDivider()
                (listOfNotNull(nicheRef, creatorRef) + tagRefs).forEach { ref ->
                    val label =
                        when {
                            ref.startsWith("creator:") -> "@${ref.removePrefix("creator:")}"
                            ref.startsWith("niche:") -> "Niche: ${ref.removePrefix("niche:").substringAfter('|')}"
                            else -> "#${ref.removePrefix("tag:")}"
                        }
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().clickable {
                                if (ref in selected) selected.remove(ref) else selected.add(ref)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = ref in selected, onCheckedChange = {
                            if (it) selected.add(ref) else selected.remove(ref)
                        })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                enabled = feedId != null && selected.isNotEmpty(),
                onClick = {
                    feedId?.let { id -> selected.forEach { ref -> onAdd(id, ref) } }
                },
            ) { Text("Add") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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

/** §8 per-feed filter dialog: duration / resolution / orientation chips. */
@Composable
private fun FeedFilterDialog(
    prefs: com.rjbiermann.giffyviewer.core.datastore.FeedPrefs,
    onApply: (com.rjbiermann.giffyviewer.core.datastore.FeedPrefs) -> Unit,
    onDismiss: () -> Unit,
) {
    var duration by remember { mutableStateOf(prefs.duration) }
    var resolution by remember { mutableStateOf(prefs.resolution) }
    var orientation by remember { mutableStateOf(prefs.orientation) }
    var shuffleSeed by remember { mutableStateOf(prefs.shuffleSeed) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter feed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Duration", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Any" to "",
                        "<10s" to "lt10",
                        "10–30s" to "10-30",
                        "30–60s" to "30-60",
                        "1–5m" to "1-5m",
                        ">5m" to "gt5m",
                    ).forEach { (label, value) ->
                        FilterChip(selected = duration == value, onClick = { duration = value }, label = { Text(label) })
                    }
                }
                Text("Resolution", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Any" to "", "HD only" to "hd").forEach { (label, value) ->
                        FilterChip(
                            selected = resolution == value,
                            onClick = { resolution = value },
                            label = { Text(label) },
                        )
                    }
                }
                Text("Shuffle", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = shuffleSeed == 0L,
                        onClick = { shuffleSeed = 0L },
                        label = { Text("Off") },
                    )
                    FilterChip(
                        selected = shuffleSeed != 0L,
                        onClick = { if (shuffleSeed == 0L) shuffleSeed = System.currentTimeMillis() },
                        label = { Text("On") },
                    )
                    if (shuffleSeed != 0L) {
                        FilterChip(
                            selected = false,
                            onClick = { shuffleSeed = System.currentTimeMillis() },
                            label = { Text("Reshuffle") },
                        )
                    }
                }
                Text("Orientation", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Global" to "",
                        "Any" to "any",
                        "Vertical" to "vertical",
                        "Horizontal" to "horizontal",
                    ).forEach { (label, value) ->
                        FilterChip(selected = orientation == value, onClick = { orientation = value }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onApply(
                    com.rjbiermann.giffyviewer.core.datastore.FeedPrefs(
                        duration = duration,
                        resolution = resolution,
                        orientation = orientation,
                        shuffleSeed = shuffleSeed,
                    ),
                )
            }) { Text("Apply") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
