package com.rjbiermann.giffyviewer.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.graphics.Color
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

/**
 * 10-foot UI (PLAN §8 TV): vertical stack of rows, D-pad navigates rows and
 * cards; center opens the player. Rows: Trending · Top This Week · Continue
 * Watching (Favorites/group/Explore rows land in Phase 7 with those features).
 */
@Composable
fun TvHomeScreen(
    onOpenGif: (list: List<Gif>, index: Int) -> Unit,
    onOpenCreator: (String) -> Unit,
    homeViewModel: TvHomeViewModel,
    continueViewModel: ContinueWatchingViewModel,
) {
    val trending = homeViewModel.trending.collectAsLazyPagingItems()
    val topThisWeek = homeViewModel.topThisWeek.collectAsLazyPagingItems()
    val favorites = homeViewModel.favorites.collectAsLazyPagingItems()
    val liked = homeViewModel.liked.collectAsLazyPagingItems()
    val followingVm: com.rjbiermann.giffyviewer.feature.feed.FollowingViewModel =
        androidx.hilt.navigation.compose
            .hiltViewModel()
    val followingCreators by followingVm.creators.collectAsStateWithLifecycle(initialValue = emptyList())
    val isLoggedIn = homeViewModel.isLoggedIn
    LaunchedEffect(Unit) { if (isLoggedIn) followingVm.refresh() }
    val exploreCreators by homeViewModel.exploreCreators.collectAsStateWithLifecycle(initialValue = emptyList())
    val continueEntries by continueViewModel.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val hasFavorites by homeViewModel.hasFavorites.collectAsStateWithLifecycle(initialValue = false)

    // Remote MENU key on a focused card opens creator quick actions.
    var actionsFor by remember { mutableStateOf<Gif?>(null) }

    LazyColumn(
        // Paint the borrowed page color — the window background is a lighter gray.
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        item {
            Text(
                text = "Giffy Viewer",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        item { FeedRow("Trending", trending, onOpenGif, onMenu = { actionsFor = it }) }
        // Explore = Top Creators (§9 lingo) — creators row, tap → creator feed.
        item { CreatorRow("Explore", exploreCreators, onOpenCreator) }
        item { FeedRow("Top This Week", topThisWeek, onOpenGif, onMenu = { actionsFor = it }) }
        // Empty-state rule: no blank favorites row when nothing is favorited.
        if (hasFavorites) {
            item { FeedRow("Favorites", favorites, onOpenGif, onMenu = { actionsFor = it }) }
        }
        // Logged-in rows (§9 TV): Liked (network-live) + Following creators.
        if (isLoggedIn) {
            item { FeedRow("Liked GIFs & Images", liked, onOpenGif, onMenu = { actionsFor = it }) }
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
                        GifCard(entry.gif, Modifier.width(cardWidth(entry.gif)), onMenu = { actionsFor = entry.gif }) {
                            onOpenGif(listOf(entry.gif), 0)
                        }
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
            onDismiss = { actionsFor = null },
        )
    }
}

/** Explore/Following rows: creator cards (avatar + @name), tap → creator feed. */
@Composable
private fun CreatorRow(
    title: String,
    creators: List<com.rjbiermann.giffyviewer.core.network.CreatorSearchItemDto>,
    onOpenCreator: (String) -> Unit,
) {
    // Same reservation rule as FeedRow: loading rows hold their space.
    Column(modifier = Modifier.padding(vertical = 8.dp).heightIn(min = ROW_RESERVED_HEIGHT)) {
        RowTitle(title)
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
                            Text(
                                "@${creator.username}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            if (creator.verified) {
                                com.rjbiermann.giffyviewer.core.ui.VerifiedTick(
                                    modifier = Modifier.padding(start = 3.dp, top = 8.dp).size(12.dp),
                                )
                            }
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
    onMenu: (Gif) -> Unit,
) {
    // Reserve the row's space while paging loads — a zero-height row that pops
    // to full height shoves every row below (homepage UI drift, user report).
    // A strict global pref (orientation "horizontal" over an all-portrait pool,
    // live-proven) can empty a row entirely — never a silent blank strip.
    Column(modifier = Modifier.padding(vertical = 8.dp).heightIn(min = ROW_RESERVED_HEIGHT)) {
        RowTitle(title)
        if (gifs.itemCount == 0 && gifs.loadState.refresh is LoadState.NotLoading) {
            Text(
                text = "No videos match your filters — Settings → Orientation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(count = gifs.itemCount, key = { i -> gifs[i]?.id ?: "pending$i" }) { i ->
                gifs[i]?.let { gif ->
                    GifCard(gif, Modifier.width(cardWidth(gif)), onMenu = { onMenu(gif) }) {
                        onOpenGif(snapshot(gifs), gifs.indexOf(gif.id))
                    }
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
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** Fixed row content height (10-foot legible) — card width derives per gif aspect. */
internal val CARD_ROW_HEIGHT_DP = 170.dp

/** FeedRow/CreatorRow reserved height (card + caption) while loading. */
internal val ROW_RESERVED_HEIGHT = 210.dp

/**
 * Mixed-orientation rows (PLAN §9 TV): card width = row height × the gif's own
 * aspect, clamped — portrait never narrower than 120dp, landscape capped at the
 * classic 280dp wide card; unknown aspect → the landscape default.
 */
internal fun cardWidth(gif: Gif): Dp =
    if (gif.width <= 0 || gif.height <= 0) {
        280.dp
    } else {
        val aspect = gif.width.toFloat() / gif.height
        if (aspect < 1f) {
            (CARD_ROW_HEIGHT_DP * aspect).coerceAtLeast(120.dp)
        } else {
            (CARD_ROW_HEIGHT_DP * aspect).coerceAtMost(280.dp)
        }
    }

@Composable
internal fun GifCard(
    gif: Gif,
    modifier: Modifier,
    onMenu: () -> Unit,
    onClick: () -> Unit,
) {
    // 10-foot UX: focused card grows so the D-pad user always sees where focus is.
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "cardScale")
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
                        .height(CARD_ROW_HEIGHT_DP)
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
                    // fixed landscape card (10-foot norm); portrait gifs crop — fine for browse
                    modifier = Modifier.fillMaxWidth().height(CARD_ROW_HEIGHT_DP),
                )
                // Audio-know-before-tap badge — mobile-tile parity (unified UI).
                if (gif.hasAudio) {
                    Box(
                        modifier =
                            Modifier
                                .align(androidx.compose.ui.Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 3.dp),
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
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = "@${gif.userName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
                if (gif.verified) {
                    com.rjbiermann.giffyviewer.core.ui.VerifiedTick(
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 3.dp, top = 4.dp).size(12.dp),
                    )
                }
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
