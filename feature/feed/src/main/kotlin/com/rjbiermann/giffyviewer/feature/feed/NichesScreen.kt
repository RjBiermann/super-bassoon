package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rjbiermann.giffyviewer.core.ui.GiffyScaffold
import com.rjbiermann.giffyviewer.core.ui.SCROLL_TO_TOP_THRESHOLD
import com.rjbiermann.giffyviewer.core.ui.ScrollToTopFab
import com.rjbiermann.giffyviewer.core.ui.rememberReducedMotion
import com.rjbiermann.giffyviewer.core.ui.rememberScrollingUp
import com.rjbiermann.giffyviewer.core.ui.scrollToTop
import kotlinx.coroutines.launch

/**
 * Niches browser: issue #47 slice 3 — preview-tile listing from
 * `v2/niches/search/previews` (the site's own /niches source, live-verified:
 * DESIGN-47s3 §1); tap opens the niche as a feed tab.
 * Site-parity 2026-10: scrollable category filter chips (`v2/niches/categories`)
 * and the Sort menu (all five live-verified orders — DESIGN-47SORTS).
 * Paging state lives in the shared NichesViewModel (mobile + TV one path).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NichesScreen(
    onBack: () -> Unit,
    onOpenNiche: (FeedSource.Niche) -> Unit,
    onOpenAbout: (id: String, name: String) -> Unit,
    joinViewModel: NicheJoinViewModel = hiltViewModel(),
    viewModel: NichesViewModel = hiltViewModel(),
) {
    val joined by joinViewModel.joined.collectAsStateWithLifecycle(initialValue = emptySet())
    LaunchedEffect(Unit) { joinViewModel.refresh() }
    val niches by viewModel.niches.collectAsStateWithLifecycle()
    val pinned by viewModel.settings.pinnedNiches.collectAsStateWithLifecycle(initialValue = emptySet())
    val category by viewModel.category.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val endReached by viewModel.endReached.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    var sortMenuOpen by remember { mutableStateOf(false) }

    // Issue #49: the page's load used to fail/hang silently — the list sat
    // empty with no indication and nothing retried. Auto-retry (same budget
    // as home rows) after each visible failure; the pending spinner below
    // makes an in-flight load visible, and callTimeout bounds any hang so
    // it degrades into the honest failure copy instead of a blind void.
    // Scroll-to-top fab (#23): the taxonomy pages can run deep; same thresholds
    // as the main feed, navigation-only.
    val listState = rememberLazyListState()
    val showFab by rememberScrollingUp(listState, SCROLL_TO_TOP_THRESHOLD)
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()

    AutoRetryEffect(
        error = loadFailed,
        success = niches.isNotEmpty() && !loadFailed,
        key = loadFailed,
        isOnline = isOnline,
        retry = { viewModel.loadMore() },
    )

    GiffyScaffold(
        title = "Niches",
        onBack = onBack,
        actions = {
            androidx.compose.material3.TextButton(onClick = { sortMenuOpen = true }) {
                Text("Sort: ${nicheListingSorts.first { it.first == sort }.second}")
            }
            androidx.compose.material3.DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false },
            ) {
                // All five orders live-verified on this endpoint (DESIGN-47SORTS §1).
                nicheListingSorts.forEach { (value, label) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            viewModel.resort(value)
                            sortMenuOpen = false
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
            ) {
                // Site-parity filter chips: All + one chip per niche category.
                item {
                    LazyRow(
                        contentPadding =
                            androidx.compose.foundation.layout
                                .PaddingValues(horizontal = 12.dp),
                        horizontalArrangement =
                            androidx.compose.foundation.layout.Arrangement
                                .spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = category == null,
                                onClick = { viewModel.selectCategory(null) },
                                label = { Text("All") },
                            )
                        }
                        items(categories) { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { viewModel.selectCategory(if (category == cat) null else cat) },
                                label = { Text(cat) },
                            )
                        }
                    }
                }
                // Preview tiles (issue #47 slice 3): two tiles per row — the
                // shared scroll helpers/fab are LazyListState-typed, so the
                // grid is chunked rows inside the existing LazyColumn.
                items(niches.chunked(2)) { rowTiles ->
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        rowTiles.forEach { tile ->
                            NicheTileCard(
                                tile = tile,
                                isPinned = pinned.any { it.startsWith("${tile.id}|") },
                                canJoin = joinViewModel.loggedIn,
                                joined = tile.id in joined,
                                onOpen = { onOpenNiche(FeedSource.Niche(tile.id, tile.name)) },
                                onJoin = { joinViewModel.toggle(tile.id) },
                                onAbout = { onOpenAbout(tile.id, tile.name) },
                                onPin = { viewModel.togglePin(tile) },
                                modifier = Modifier.weight(1f),
                            )
                            if (rowTiles.size > 1 && tile != rowTiles.last()) {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                        // Keep pairs aligned: an empty spacer fills the odd row.
                        if (rowTiles.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
                if (niches.isEmpty() && !loadFailed && loading) {
                    item(key = "pending") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                if (niches.isEmpty() && !loadFailed && !loading && endReached) {
                    item(key = "empty") {
                        com.rjbiermann.giffyviewer.core.ui.EmptyState(
                            message = "No niches",
                            hint = "Check back later",
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
                if (loadFailed) {
                    item(key = "error") {
                        com.rjbiermann.giffyviewer.core.ui.EmptyState(
                            message = "Couldn't load niches",
                            hint = "Check your connection and retry",
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                        TextButton(
                            onClick = { viewModel.loadMore() },
                            modifier = Modifier.padding(16.dp),
                        ) { Text("Retry") }
                    }
                }
                if (!endReached) {
                    item(key = "load-more") {
                        TextButton(
                            onClick = { viewModel.loadMore() },
                            modifier = Modifier.padding(16.dp),
                        ) { Text("Load more") }
                    }
                } else {
                    item(key = "end") {
                        Text(
                            "That's all ${niches.size} niches",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            ScrollToTopFab(
                visible = showFab,
                onClick = {
                    scope.launch { listState.scrollToTop(reducedMotion) }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * One preview tile (issue #47 slice 3): site ellipsis — poster image + title
 * (A1), with the app-superior counts kept and every pre-listing action intact.
 * Image = preview-gif poster / niche thumbnail / niche cover (DESIGN-47s3 §2);
 * Coil handles loading, failure leaves the name-only tile.
 */
@Composable
private fun NicheTileCard(
    tile: NicheTile,
    isPinned: Boolean,
    canJoin: Boolean,
    joined: Boolean,
    onOpen: () -> Unit,
    onJoin: () -> Unit,
    onAbout: () -> Unit,
    onPin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onOpen).padding(vertical = 8.dp),
    ) {
        AsyncImage(
            model = tile.imageUrl,
            contentDescription = "niche ${tile.name}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
        )
        Text(
            tile.name,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "${tile.gifs} gifs · ${tile.subscribers} members",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // §9 lingo: Join Niche / Leave Niche (logged-in only).
            if (canJoin) {
                TextButton(onClick = onJoin) { Text(if (joined) "Leave" else "Join") }
            }
            IconButton(onClick = onAbout) {
                Icon(Icons.Outlined.Info, contentDescription = "about ${tile.name}")
            }
            IconButton(onClick = onPin) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    // Issue #51: name the object — "pin" alone is ambiguous in
                    // a scrolling list (sibling buttons name theirs).
                    contentDescription = if (isPinned) "unpin ${tile.name}" else "pin ${tile.name}",
                )
            }
        }
    }
}
