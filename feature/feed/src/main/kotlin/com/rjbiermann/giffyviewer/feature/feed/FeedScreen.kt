package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/**
 * M3 (m3.material.io): Scaffold + small TopAppBar + FilterChip feed tabs +
 * linear refresh indicator. Masonry 2-col portrait / 3-col landscape (PLAN §9).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    onOpenPlayer: (Int) -> Unit = {},
    onOpenAccount: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val items = viewModel.gifs.collectAsLazyPagingItems()
    val showBlockHint by viewModel.showBlockHint.collectAsStateWithLifecycle()
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(FeedSource.Trending, FeedSource.Discover, FeedSource.Favorites).forEach { candidate ->
                    FilterChip(
                        selected = source == candidate,
                        onClick = { viewModel.open(candidate) },
                        label = { Text(candidate.title()) },
                    )
                }
            }

            // M3 linear indicator for refresh / append activity
            if (items.loadState.refresh is LoadState.Loading ||
                items.loadState.append is LoadState.Loading
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                )
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
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
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
            }
        }
    }
}

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
private fun QuickBlockSheet(
    gif: Gif,
    onDismiss: () -> Unit,
    viewModel: FeedViewModel,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                "@${gif.userName}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            val favState by viewModel
                .creatorState(gif.userName)
                .collectAsState(initial = null)
            listStyle(if (favState == "FAVORITED") "Unfavorite @${gif.userName}" else "Favorite @${gif.userName}") {
                viewModel.toggleFavoriteCreator(gif.userName)
                onDismiss()
            }
            listStyle(
                "Block creator",
            ) {
                viewModel.blockCreator(gif.userName)
                onDismiss()
            }
            gif.tags.take(3).forEach { tag ->
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
