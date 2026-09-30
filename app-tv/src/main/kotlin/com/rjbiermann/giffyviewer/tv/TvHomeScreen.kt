package com.rjbiermann.giffyviewer.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rjbiermann.giffyviewer.core.model.Gif
import androidx.compose.material3.Surface as M3Surface

/**
 * 10-foot UI (PLAN §8 TV): vertical stack of rows, D-pad navigates rows and
 * cards; center opens the player. Rows: Trending · Discover · Continue Watching
 * (Favorites/group rows land in Phase 7 with those features).
 */
@Composable
fun TvHomeScreen(
    onOpenGif: (list: List<Gif>, index: Int) -> Unit,
    homeViewModel: TvHomeViewModel,
    continueViewModel: ContinueWatchingViewModel,
) {
    val trending = homeViewModel.trending.collectAsLazyPagingItems()
    val discover = homeViewModel.discover.collectAsLazyPagingItems()
    val topThisWeek = homeViewModel.topThisWeek.collectAsLazyPagingItems()
    val favorites = homeViewModel.favorites.collectAsLazyPagingItems()
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
                modifier = Modifier.padding(24.dp),
            )
        }
        item { FeedRow("Trending", trending, onOpenGif, onMenu = { actionsFor = it }) }
        item { FeedRow("Discover", discover, onOpenGif, onMenu = { actionsFor = it }) }
        item { FeedRow("Top This Week", topThisWeek, onOpenGif, onMenu = { actionsFor = it }) }
        // Empty-state rule: no blank favorites row when nothing is favorited.
        if (hasFavorites) {
            item { FeedRow("Favorites", favorites, onOpenGif, onMenu = { actionsFor = it }) }
        }
        item {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                RowTitle("Continue Watching")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(continueEntries, key = { it.gif.id }) { entry ->
                        GifCard(entry.gif, onMenu = { actionsFor = entry.gif }) {
                            onOpenGif(listOf(entry.gif), 0)
                        }
                    }
                }
            }
        }
    }

    actionsFor?.let { gif ->
        QuickActionsDialog(
            gif = gif,
            viewModel = homeViewModel,
            onDismiss = { actionsFor = null },
        )
    }
}

@Composable
private fun QuickActionsDialog(
    gif: Gif,
    viewModel: TvHomeViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel
        .creatorState(gif.userName)
        .collectAsStateWithLifecycle(initialValue = null)
    Dialog(onDismissRequest = onDismiss) {
        M3Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            modifier = Modifier.width(360.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "@${gif.userName}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { first.requestFocus() }
                Button(
                    onClick = {
                        viewModel.toggleFavoriteCreator(gif.userName)
                        onDismiss()
                    },
                    modifier = Modifier.focusRequester(first).padding(vertical = 4.dp),
                ) {
                    Text(if (state == "FAVORITED") "Unfavorite" else "Favorite")
                }
                Button(
                    onClick = {
                        viewModel.blockCreator(gif.userName)
                        onDismiss()
                    },
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Text("Block creator")
                }
                Button(onClick = onDismiss, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("Cancel")
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
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        RowTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(count = gifs.itemCount, key = { i -> gifs[i]?.id ?: "pending$i" }) { i ->
                gifs[i]?.let { gif ->
                    GifCard(gif, onMenu = { onMenu(gif) }) {
                        onOpenGif(snapshot(gifs), gifs.indexOf(gif.id))
                    }
                }
            }
        }
    }
}

/** Snapshot of the loaded pages for D-pad walking inside the player. */
private fun snapshot(gifs: LazyPagingItems<Gif>): List<Gif> =
    buildList {
        for (i in 0 until gifs.itemCount) gifs[i]?.let { add(it) }
    }

private fun LazyPagingItems<Gif>.indexOf(id: String): Int {
    for (i in 0 until itemCount) if (get(i)?.id == id) return i
    return 0
}

@Composable
private fun RowTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun GifCard(
    gif: Gif,
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
            Modifier
                .width(280.dp)
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
                        .width(280.dp)
                        .height(170.dp)
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
                    modifier = Modifier.width(280.dp).height(170.dp),
                )
            }
            Text(
                text = "@${gif.userName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
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
