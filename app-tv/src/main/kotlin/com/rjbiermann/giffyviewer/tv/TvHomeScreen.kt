package com.rjbiermann.giffyviewer.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.ui.AudioBadge
import com.rjbiermann.giffyviewer.core.ui.CreatorLabel
import com.rjbiermann.giffyviewer.core.ui.avgColorOr
import com.rjbiermann.giffyviewer.core.ui.giffyFocus
import com.rjbiermann.giffyviewer.feature.feed.FeedSource

/**
 * 10-foot UI (PLAN §8 TV): vertical stack of rows, D-pad navigates rows and
 * cards; center opens the player. Rows: Trending · Top This Week · Continue
 * Watching (Favorites/group/Explore rows land in Phase 7 with those features).
 */
@Composable
fun TvHomeScreen(
    onOpenGif: (list: List<Gif>, index: Int) -> Unit,
    onOpenCreator: (String) -> Unit,
    /** Show-more pane niche rows (quick actions). */
    onOpenNiche: (com.rjbiermann.giffyviewer.feature.feed.FeedSource.Niche) -> Unit = {},
    /** Slice 10: quick-actions "Open feed" — the row's FeedSource full-page. */
    onOpenFeed: (com.rjbiermann.giffyviewer.feature.feed.FeedSource) -> Unit = {},
    /** Preview-on-focus (AGENTS-UX-PATTERNS): null factory = no previews. */
    playerFactory: com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory? = null,
    dataSaver: Boolean = false,
    homeViewModel: TvHomeViewModel,
    continueViewModel: ContinueWatchingViewModel,
    /** Hoisted at TvMainActivity Root so BACK from a player/full feed keeps
     *  scroll position (branch swaps otherwise discard remember-ed states). */
    listState: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy
            .rememberLazyListState(),
) {
    // Row-height setting (slice 11): DataStore pref → effective card height.
    val tvRowHeightPref by homeViewModel.tvRowHeight.collectAsStateWithLifecycle("default")
    val rowHeight = rowHeightFor(tvRowHeightPref)
    val trending = homeViewModel.trending.collectAsLazyPagingItems()
    val topThisWeek = homeViewModel.topThisWeek.collectAsLazyPagingItems()
    val favorites = homeViewModel.favorites.collectAsLazyPagingItems()
    val liked = homeViewModel.liked.collectAsLazyPagingItems()
    // For You (server personalization; TV↔mobile parity): first row when
    // logged in — same home order as mobile's site-verified sweep.
    val forYou = homeViewModel.forYou.collectAsLazyPagingItems()
    val followingVm: com.rjbiermann.giffyviewer.feature.feed.FollowingViewModel =
        androidx.hilt.navigation.compose
            .hiltViewModel()
    val followingCreators by followingVm.creators.collectAsStateWithLifecycle(initialValue = emptyList())
    val isLoggedIn by homeViewModel.isLoggedIn.collectAsStateWithLifecycle(false)
    // Sign-in refreshes the Following row (Liked row rides its own token flow).
    LaunchedEffect(isLoggedIn) { if (isLoggedIn) followingVm.refresh() }
    val exploreCreators by homeViewModel.exploreCreators.collectAsStateWithLifecycle(initialValue = emptyList())
    val exploreFailed by homeViewModel.exploreFailed.collectAsStateWithLifecycle(false)
    // SLICE-12: effective §6/§8 orientation per row (per-feed pref merged over
    // the global pref) — the filtered-empty hint names the ACTUAL filter.
    val trendingOrientation by homeViewModel.trendingOrientation.collectAsStateWithLifecycle("any")
    val topThisWeekOrientation by homeViewModel.topThisWeekOrientation.collectAsStateWithLifecycle("any")
    val favoritesOrientation by homeViewModel.favoritesOrientation.collectAsStateWithLifecycle("any")
    val likedOrientation by homeViewModel.likedOrientation.collectAsStateWithLifecycle("any")
    val forYouOrientation by homeViewModel.forYouOrientation.collectAsStateWithLifecycle("any")
    val continueEntries by continueViewModel.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val hasFavorites by homeViewModel.hasFavorites.collectAsStateWithLifecycle(initialValue = false)

    // Remote MENU key on a focused card opens creator quick actions.
    var actionsFor by remember { mutableStateOf<Gif?>(null) }
    // The row's feed source for the panel's "Open feed" row (slice 10).
    var actionsFeed by remember { mutableStateOf<com.rjbiermann.giffyviewer.feature.feed.FeedSource?>(null) }

    fun openActions(
        gif: Gif,
        source: com.rjbiermann.giffyviewer.feature.feed.FeedSource?,
    ) {
        actionsFor = gif
        actionsFeed = source
    }

    // Preview-on-focus: one shared player for the whole screen, moved gif
    // to gif on the settled (600ms) focus — data-saver keeps posters.
    val previewContext = androidx.compose.ui.platform.LocalContext.current
    val preview =
        remember(playerFactory) {
            if (playerFactory == null) {
                null
            } else {
                FocusPreview(playerFactory, previewContext)
            }
        }
    androidx.compose.runtime.DisposableEffect(preview) {
        onDispose { preview?.release() }
    }

    LazyColumn(
        // Paint the borrowed page color — the window background is a lighter gray.
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        item {
            Text(
                text = "Giffy Viewer",
                // F8 (batch 15): same slot as mobile's GiffyScaffold title.
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        // Logged-in For You row goes FIRST (site home order, verified sweep);
        // anonymous users never see it (the feed 401s anonymous). The scope
        // chip row rides under THIS row's title only (§7 = feed-local pref).
        if (isLoggedIn) {
            item {
                FeedRow(
                    "For You",
                    forYou,
                    onOpenGif,
                    onMenu = ::openActions,
                    preview = preview,
                    dataSaver = dataSaver,
                    scopeChips = { ForYouScopeRow(homeViewModel) },
                    rowHeight = rowHeight,
                    source = FeedSource.ForYou,
                    orientation = forYouOrientation,
                )
            }
        }
        item {
            FeedRow(
                "Trending",
                trending,
                onOpenGif,
                onMenu = ::openActions,
                preview = preview,
                dataSaver = dataSaver,
                rowHeight = rowHeight,
                source = FeedSource.Trending,
                orientation = trendingOrientation,
            )
        }
        // Explore = Top Creators (§9 lingo) — creators row, tap → creator feed.
        item { CreatorRow("Explore", exploreCreators, onOpenCreator, failed = exploreFailed, onRetry = { homeViewModel.refreshExplore() }) }
        item {
            FeedRow(
                "Top This Week",
                topThisWeek,
                onOpenGif,
                onMenu = ::openActions,
                preview = preview,
                dataSaver = dataSaver,
                rowHeight = rowHeight,
                source = FeedSource.TopThisWeek,
                orientation = topThisWeekOrientation,
            )
        }
        // Empty-state rule: no blank favorites row when nothing is favorited.
        if (hasFavorites) {
            item {
                FeedRow(
                    "Favorites",
                    favorites,
                    onOpenGif,
                    onMenu = ::openActions,
                    preview = preview,
                    dataSaver = dataSaver,
                    rowHeight = rowHeight,
                    source = FeedSource.Favorites,
                    orientation = favoritesOrientation,
                )
            }
        }
        // Logged-in rows (§9 TV): Liked (network-live) + Following creators.
        if (isLoggedIn) {
            item {
                FeedRow(
                    "Liked GIFs & Images",
                    liked,
                    onOpenGif,
                    onMenu = ::openActions,
                    preview = preview,
                    dataSaver = dataSaver,
                    rowHeight = rowHeight,
                    source = FeedSource.Liked,
                    orientation = likedOrientation,
                )
            }
            item { CreatorRow("Following", followingCreators.map { it }, onOpenCreator) }
        }
        item {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                RowTitle("Continue Watching")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(continueEntries, key = { it.gif.id }) { entry ->
                        GifCard(
                            entry.gif,
                            Modifier.width(cardWidth(entry.gif, rowHeight)),
                            onMenu = { openActions(entry.gif, com.rjbiermann.giffyviewer.feature.feed.FeedSource.Continue) },
                            onClick = { onOpenGif(listOf(entry.gif), 0) },
                            rowHeight = rowHeight,
                        )
                    }
                }
            }
        }
    }

    actionsFor?.let { gif ->
        val quickVm: com.rjbiermann.giffyviewer.feature.feed.FeedViewModel =
            androidx.hilt.navigation.compose
                .hiltViewModel()
        TvQuickActionsDialog(
            gif = gif,
            feedViewModel = quickVm,
            onDismiss = {
                actionsFor = null
                actionsFeed = null
            },
            onOpenCreator = onOpenCreator,
            onOpenNiche = onOpenNiche,
            onOpenFeed = { source ->
                actionsFor = null
                actionsFeed = null
                onOpenFeed(source)
            },
            feedSource = actionsFeed,
        )
    }
}

/** Explore/Following rows: creator cards (avatar + @name), tap → creator feed. */
@Composable
private fun CreatorRow(
    title: String,
    creators: List<com.rjbiermann.giffyviewer.core.network.CreatorSearchItemDto>,
    onOpenCreator: (String) -> Unit,
    failed: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    // Same reservation rule as FeedRow: loading rows hold their space.
    Column(modifier = Modifier.padding(vertical = 8.dp).heightIn(min = ROW_RESERVED_HEIGHT)) {
        RowTitle(title)
        if (creators.isEmpty() && failed) {
            Text(
                text = "Couldn't load $title",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
            onRetry?.let {
                androidx.compose.material3.TextButton(onClick = it, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Retry")
                }
            }
        }
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(creators.size, key = { i -> creators[i].username }) { i ->
                val creator = creators[i]
                Card(onClick = { onOpenCreator(creator.username) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(10.dp)) {
                        AsyncImage(
                            model = creator.profileImageUrl,
                            contentDescription = "creator ${creator.username}",
                            modifier = Modifier.size(96.dp).clip(CircleShape),
                        )
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CreatorLabel(
                                creator.username,
                                creator.verified,
                                tickSize = 12.dp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Text(
                            "${creator.followers} followers · ${creator.gifs} gifs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRow(
    title: String,
    gifs: LazyPagingItems<Gif>,
    onOpenGif: (List<Gif>, Int) -> Unit,
    /** Menu select carries the row's feed source (quick-actions "Open feed"). */
    onMenu: (Gif, FeedSource?) -> Unit,
    /** The row's own feed (slice 10) — null = row without an openable source. */
    source: FeedSource? = null,
    /** Preview-on-focus state (null = feature off). */
    preview: FocusPreview? = null,
    dataSaver: Boolean = false,
    /** Optional content under the title, above the cards (For You scope chips). */
    scopeChips: (@Composable () -> Unit)? = null,
    /** Effective card height (slice 11 row-size setting). */
    rowHeight: Dp = CARD_ROW_HEIGHT_DP,
    /** SLICE-12: effective §6/§8 orientation for THIS row's feed — drives the
     *  honest filtered-empty copy ("any" → generic hint, strict value → names
     *  the actual filter, per-feed pref vs global Settings distinguished). */
    orientation: String = "any",
) {
    // Reserve the row's space while paging loads — a zero-height row that pops
    // to full height shoves every row below (homepage UI drift, user report).
    // A strict global pref (orientation "horizontal" over an all-portrait pool,
    // live-proven) can empty a row entirely — never a silent blank strip.
    Column(modifier = Modifier.padding(vertical = 8.dp).heightIn(min = reservedHeight(rowHeight))) {
        RowTitle(title)
        scopeChips?.invoke()
        if (gifs.itemCount == 0 && gifs.loadState.refresh is LoadState.NotLoading) {
            // SLICE-12 (user report): the old copy blamed "Settings →
            // Orientation" unconditionally — wrong when a per-feed §8 pref is
            // the cause (global Settings shows unset) and wrong when NO
            // orientation filter is effective at all (another drop emptied the
            // row: ContentFilter blocks, verified-only, dead pool). Never
            // claim orientation when the effective orientation is "any".
            Text(
                text = filteredEmptyHint(orientation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
        if (gifs.loadState.refresh is LoadState.Error) {
            // Offline home row: explain + one-press retry (Niches parity).
            Text(
                text = "Couldn't load $title",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
            androidx.compose.material3.TextButton(
                onClick = { gifs.retry() },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Retry") }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(count = gifs.itemCount, key = { i -> gifs[i]?.id ?: "pending$i" }) { i ->
                gifs[i]?.let { gif ->
                    GifCard(
                        gif,
                        Modifier.width(cardWidth(gif, rowHeight)),
                        onMenu = { onMenu(gif, source) },
                        onClick = { onOpenGif(snapshot(gifs), gifs.indexOf(gif.id)) },
                        preview = preview,
                        dataSaver = dataSaver,
                        rowHeight = rowHeight,
                    )
                }
            }
        }
    }
}

/** Snapshot of the loaded pages for D-pad walking inside the player. */
internal fun snapshot(gifs: LazyPagingItems<Gif>): List<Gif> =
    buildList {
        for (i in 0 until gifs.itemCount) gifs[i]?.let { add(it) }
    }

internal fun LazyPagingItems<Gif>.indexOf(id: String): Int {
    for (i in 0 until itemCount) if (get(i)?.id == id) return i
    return 0
}

@Composable
private fun RowTitle(text: String) {
    Text(
        text = text,
        // F8 (batch 15): same slot as mobile's section headers (titleSmall).
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/**
 * For You scope chips (§7 Creators·Niches·All, logged-in home): same three
 * labels as the mobile FeedScreen selector row, writing the SAME
 * settings.forYouScope pref through TvHomeViewModel (mobile FeedViewModel
 * parity — no duplicated pref logic). Renders under the For You row title.
 * M3 FilterChip + the shared giffyFocus ring (the TvSourceFeedScreen
 * FilterChip D-pad pattern).
 */
@Composable
private fun ForYouScopeRow(homeViewModel: TvHomeViewModel) {
    val scope by homeViewModel.forYouScope.collectAsStateWithLifecycle("all")
    Row(
        modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("All" to "all", "Creators" to "creators", "Niches" to "niches").forEach { (label, value) ->
            val interaction = remember { MutableInteractionSource() }
            FilterChip(
                selected = scope == value,
                onClick = { homeViewModel.setForYouScope(value) },
                label = { Text(label) },
                interactionSource = interaction,
                modifier = Modifier.giffyFocus(interaction, shape = RoundedCornerShape(8.dp)),
            )
        }
    }
}

/** Fixed row content height (10-foot legible) — card width derives per gif aspect. */
internal val CARD_ROW_HEIGHT_DP = 170.dp

/** FeedRow/CreatorRow reserved height (card + caption) while loading. */
internal val ROW_RESERVED_HEIGHT = 210.dp

/**
 * SLICE-12 filtered-empty copy: names the ACTUAL effective orientation (the
 * per-feed §8 pref merged over the global §6 one, delivered already merged
 * via TvHomeViewModel's repository sink). "any" → the generic copy — another
 * drop (ContentFilter blocks, verified-only, pool dead-end) emptied the row
 * and the hint must NOT claim an orientation filter that isn't set.
 */
internal fun filteredEmptyHint(orientation: String): String =
    if (orientation == "any") {
        "No videos match your filters"
    } else {
        "No videos match your filters — orientation: $orientation"
    }

/**
 * Row-size setting (slice 11, DataStore `tv_row_height`): DEFAULT keeps the
 * current 170dp 10-foot card; Large/XL scale it for farther viewports. Pure
 * mapping so the Settings choice is unit-testable.
 */
internal fun rowHeightFor(pref: String): Dp =
    when (pref) {
        "large" -> CARD_ROW_HEIGHT_DP * 1.4f
        "xl" -> CARD_ROW_HEIGHT_DP * 1.8f
        else -> CARD_ROW_HEIGHT_DP
    }

/** Loading-reserved height scales with the row-size setting (card + caption). */
internal fun reservedHeight(rowHeight: Dp): Dp = rowHeight + (ROW_RESERVED_HEIGHT - CARD_ROW_HEIGHT_DP)

/**
 * Mixed-orientation rows (PLAN §9 TV): card width = row height × the gif's own
 * aspect, clamped — portrait never narrower than 120dp, landscape capped at the
 * classic 280dp wide card; unknown aspect → the landscape default.
 */
internal fun cardWidth(
    gif: Gif,
    rowHeight: Dp = CARD_ROW_HEIGHT_DP,
): Dp =
    if (gif.width <= 0 || gif.height <= 0) {
        280.dp
    } else {
        val aspect = gif.width.toFloat() / gif.height
        if (aspect < 1f) {
            (rowHeight * aspect).coerceAtLeast(120.dp)
        } else {
            (rowHeight * aspect).coerceAtMost(280.dp)
        }
    }

@Composable
internal fun GifCard(
    gif: Gif,
    modifier: Modifier,
    onMenu: () -> Unit,
    onClick: () -> Unit,
    /** Preview-on-focus state (null = feature off). */
    preview: FocusPreview? = null,
    dataSaver: Boolean = false,
    /** Poster/card height (slice 11 row-size setting; default = 170dp). */
    rowHeight: Dp = CARD_ROW_HEIGHT_DP,
) {
    // 10-foot UX: focused card grows so the D-pad user always sees where focus is.
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "cardScale")
    // Menu-less-remote keymap: hold-Center ≥500ms opens the quick-actions
    // panel (the same panel MENU opens); short-tap Center keeps the card open.
    val centerHold = remember { CenterHold(onHold = onMenu) }
    // Preview-on-focus: the SETTLED focus (600ms dwell) starts the muted loop;
    // unfocusing (or data-saver engaging) clears it (fast walking never decodes).
    // The settledId guard inside onUnsettled makes a late un-settle from a
    // walked-away card harmless to a newer settle elsewhere (T5).
    LaunchedEffect(focused, dataSaver, preview?.enabled) {
        if (preview == null || !preview.enabled) {
            return@LaunchedEffect
        }
        if (!focused || dataSaver) {
            preview.onUnsettled(gif.id)
        } else {
            kotlinx.coroutines.delay(600)
            preview.onSettled(gif, dataSaver)
        }
    }
    Card(
        onClick = onClick,
        // Mobile tiles use 12dp rounded corners (GifTile in feature:feed).
        shape =
            androidx.tv.material3.CardDefaults.shape(
                androidx.compose.foundation.shape
                    .RoundedCornerShape(12.dp),
                androidx.compose.foundation.shape
                    .RoundedCornerShape(16.dp),
            ),
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    // Remote MENU button = creator quick actions (favorite/block).
                    if (event.type == KeyEventType.KeyUp && event.key == Key.Menu) {
                        onMenu()
                        true
                    } else if (event.key == Key.DirectionCenter) {
                        // Menu-less-remote keymap: Center KeyDown is consumed
                        // (the hold clock owns the press); KeyUp either
                        // suppresses (hold fired the panel) or opens the card
                        // as before.
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                if (event.nativeKeyEvent.repeatCount == 0) centerHold.down()
                                true
                            }
                            KeyEventType.KeyUp -> {
                                if (!centerHold.up()) onClick()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                },
    ) {
        Column {
            // avgColor placeholder: no black flash on slow cells.
            // Clip: the poster must round with the card (mobile GifTile parity).
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(avgColorOr(gif.avgColor, MaterialTheme.colorScheme.surfaceVariant)),
            ) {
                // Poster ALWAYS composed, UNDER the preview surface (mobile GifTile
                // parity): the preview's transparent TextureView lets it show
                // through attach/prepare until the first video frame — no black
                // frame before the preview starts (T4), no poster swap at all.
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(gif.posterUrl)
                            .crossfade(200)
                            .build(),
                    // Decorative: the visible "@user" Text announces the creator —
                    // saying both duplicates the name per card (mobile-parity fix).
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // fixed landscape card (10-foot norm); portrait gifs crop — fine for browse
                    modifier = Modifier.fillMaxWidth().height(rowHeight),
                )
                if (preview != null && preview.settledId == gif.id && preview.player != null) {
                    // Settled-focus preview: the muted+looped SD loop OVERLAYS the
                    // poster until focus moves away (or the real player opens).
                    // focus_preview_player.xml: texture_view + transparent shutter
                    // (poster shows through until the first video frame, T4) and
                    // resize_mode=zoom — fill+center-crop, the same fill/crop
                    // semantics as the poster's ContentScale.Crop, so the video
                    // frames land exactly where the poster rendered (no jump at
                    // the settle swap, T6).
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.view.LayoutInflater
                                .from(ctx)
                                .inflate(
                                    com.rjbiermann.giffyviewer.tv.R.layout.focus_preview_player,
                                    null,
                                    false,
                                ).apply {
                                    isClickable = false
                                    isFocusable = false
                                }
                        },
                        update = { view -> (view as androidx.media3.ui.PlayerView).player = preview.player },
                        modifier = Modifier.matchParentSize(),
                    )
                }
                // Audio-know-before-tap badge — mobile-tile parity (unified UI).
                if (gif.hasAudio) {
                    AudioBadge(modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(8.dp))
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CreatorLabel(
                    gif.userName,
                    gif.verified,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    tickTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    tickSize = 12.dp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }
        }
    }
}
