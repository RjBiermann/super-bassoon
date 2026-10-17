package com.rjbiermann.giffyviewer.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import com.rjbiermann.giffyviewer.core.ui.giffyFocus
import com.rjbiermann.giffyviewer.feature.feed.AutoRetryEffect
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import com.rjbiermann.giffyviewer.feature.feed.NichesViewModel

/** Mobile NichesScreen's sort-label rendering ("alphabetical_asc" → "Alphabetical"). */
private fun sortLabel(sort: String): String = sort.removeSuffix("_asc").removeSuffix("_desc").replaceFirstChar { it.uppercase() }

/**
 * Niches browser for TV (mobile NichesScreen parity): issue #47 slice 3 —
 * preview tiles from `v2/niches/search/previews` (DESIGN-47s3). D-pad: Enter
 * opens the niche feed; MENU/hold-Center toggles the home-tab pin (shows in
 * the top pill row). State lives in the shared NichesViewModel (feature:feed).
 */
@Composable
fun TvNichesScreen(
    onOpenNiche: (FeedSource.Niche) -> Unit,
    onOpenNicheAbout: (id: String, name: String) -> Unit = { _, _ -> },
    viewModel: NichesViewModel = hiltViewModel(),
) {
    val niches by viewModel.niches.collectAsStateWithLifecycle()
    val endReached by viewModel.endReached.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pinned by viewModel.settings.pinnedNiches.collectAsStateWithLifecycle(initialValue = emptySet())
    // Site-parity category chips + sort (#47 slice 2): state lives in the
    // shared NichesViewModel — selectCategory/resort restart() the list and
    // the existing loadMore/LaunchedEffect paging continues unchanged.
    val category by viewModel.category.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    var sortMenuOpen by remember { mutableStateOf(false) }

    // D-pad browsing has no tap-to-load-more: fetch as focus nears the end.
    LaunchedEffect(niches.size, endReached) {
        if (niches.isNotEmpty() && !endReached) viewModel.loadMore()
    }
    LaunchedEffect(Unit) { viewModel.loadMore() }

    // Issue #49 (the silent-empty page): visible pending state while a load
    // is in flight, and the shared auto-retry budget after a visible
    // failure. callTimeout bounds any hang so it degrades into the
    // honest error copy instead of a blind void.
    AutoRetryEffect(
        error = loadFailed,
        success = niches.isNotEmpty() && !loadFailed,
        key = loadFailed,
        isOnline = isOnline,
        retry = { viewModel.loadMore() },
    )

    // Initial D-pad focus (AGENTS-APP pattern): without it the list sits
    // unfocused — the first CENTER press did nothing and no focus ring showed.
    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(niches.isNotEmpty()) {
        if (niches.isNotEmpty()) firstRowFocus.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Niches",
            // F8 (batch 15): same slot as mobile's GiffyScaffold title.
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        // #47 slice 2: category chips + sort, mobile NichesScreen parity. The
        // sort list mirrors mobile's menu (mobile keeps it inline in
        // NichesScreen, which is outside this slice's fence); values are the
        // verified taxonomy orders.
        // Chips = M3 FilterChip + giffyFocus ring (the shared-module chip
        // exception) so the D-pad focus treatment matches the other M3-hosted
        // rows; Sort = tv Button + M3 DropdownMenu (a changed sort restarts
        // the list in NichesViewModel).
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    val allInteraction = remember { MutableInteractionSource() }
                    FilterChip(
                        selected = category == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("All") },
                        interactionSource = allInteraction,
                        modifier = Modifier.giffyFocus(allInteraction, shape = RoundedCornerShape(8.dp)),
                    )
                }
                items(categories) { cat ->
                    val interaction = remember { MutableInteractionSource() }
                    FilterChip(
                        selected = category == cat,
                        onClick = { viewModel.selectCategory(if (category == cat) null else cat) },
                        label = { Text(cat) },
                        interactionSource = interaction,
                        modifier = Modifier.giffyFocus(interaction, shape = RoundedCornerShape(8.dp)),
                    )
                }
            }
            Box {
                Button(
                    onClick = { sortMenuOpen = true },
                    scale = ButtonScale.None,
                    colors =
                        ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    border =
                        ButtonDefaults.border(
                            focusedBorder =
                                Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary)),
                        ),
                ) { Text("Sort: ${sortLabel(sort)}") }
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false },
                ) {
                    // Previews-verified orders only — mobile parity (issue #47
                    // slice 3; DESIGN-47s3 §1: the taxonomy-only sorts were
                    // never live-checked on this endpoint).
                    listOf(
                        "subscribers" to "Subscribers",
                        "random" to "Randomize",
                    ).forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.resort(value)
                                sortMenuOpen = false
                            },
                        )
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            itemsIndexed(niches, key = { _, n -> n.id }) { index, niche ->
                val isPinned = pinned.any { it.startsWith("${niche.id}|") }
                val rowInteraction = remember { MutableInteractionSource() }
                // Menu-less-remote keymap: long-press Center (≥500ms, fires on
                // release) = the row's MENU action (toggle the home pin);
                // short-tap Center opens the niche feed (same as the click).
                val centerHold =
                    remember(niche.id) {
                        CenterHold { viewModel.togglePin(niche) }
                    }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Name block = one D-pad focusable (open feed); About sits
                    // beside it as the second focusable — RIGHT moves to it,
                    // DOWN moves rows. The old full-row focusable swallowed
                    // the inner button (directional search never entered it).
                    // Focus = issue-#7 tokens on tv-material Surface (§3.1):
                    // focused fill primaryContainer, focused border/glow primary;
                    // the old surfaceVariant fill-on-focus is retired.
                    val rowFocused by rowInteraction.collectIsFocusedAsState()
                    Surface(
                        onClick = { onOpenNiche(FeedSource.Niche(niche.id, niche.name)) },
                        scale = ClickableSurfaceScale.None,
                        interactionSource = rowInteraction,
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.background,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        border =
                            ClickableSurfaceDefaults.border(
                                focusedBorder =
                                    Border(
                                        BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                                    ),
                            ),
                        modifier =
                            Modifier
                                .weight(1f)
                                .then(if (index == 0) Modifier.focusRequester(firstRowFocus) else Modifier)
                                .onPreviewKeyEvent { event ->
                                    // MENU toggles the home pin (creator-quick-action pattern).
                                    if (event.type == KeyEventType.KeyUp && event.key == Key.Menu) {
                                        viewModel.togglePin(niche)
                                        true
                                    } else if (event.key == Key.DirectionCenter) {
                                        // Menu-less-remote keymap: Center KeyDown
                                        // is consumed (hold clock owns the press);
                                        // KeyUp either suppresses (hold fired the
                                        // pin toggle) or opens the niche feed.
                                        when (event.type) {
                                            KeyEventType.KeyDown -> {
                                                if (event.nativeKeyEvent.repeatCount == 0) centerHold.down()
                                                true
                                            }
                                            KeyEventType.KeyUp -> {
                                                if (!centerHold.up()) onOpenNiche(FeedSource.Niche(niche.id, niche.name))
                                                true
                                            }
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }.padding(vertical = 8.dp),
                    ) {
                        // Surface's content scope is NOT a layout container —
                        // two children place at the same position (device pass
                        // 2026-10-16: name drawn over the stats line). Own the
                        // stacking with a Column.
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            // #47 slice 3: preview-tile image above the name
                            // (site /niches tile = poster + title; static
                            // poster here — no inline video on TV niche rows).
                            if (niche.imageUrl != null) {
                                AsyncImage(
                                    model = niche.imageUrl,
                                    contentDescription = "niche ${niche.name}",
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(12.dp)),
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = niche.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color =
                                        if (rowFocused) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                )
                                if (isPinned) {
                                    // Vector pin (emoji-prefix anti-pattern; announced oddly).
                                    androidx.compose.material3.Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Filled.PushPin,
                                        contentDescription = "pinned",
                                        tint =
                                            if (rowFocused) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        modifier = Modifier.padding(start = 6.dp).size(14.dp),
                                    )
                                }
                            }
                            Text(
                                text = "${niche.gifs} gifs · ${niche.subscribers} subscribers",
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    if (rowFocused) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    // Parity: mobile opens niche About from the player cluster;
                    // TV gets an explicit About focusable (D-pad has no tap).
                    TextButton(onClick = { onOpenNicheAbout(niche.id, niche.name) }) {
                        Text("About")
                    }
                }
            }
            if (niches.isEmpty() && !loadFailed && loading) {
                item(key = "pending") {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            if (niches.isEmpty() && !loadFailed && !loading && endReached) {
                item(key = "empty") {
                    Text(
                        "No niches — check back later",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (loadFailed) {
                item(key = "error") {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        Text(
                            "Couldn't load niches — check your connection",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { viewModel.loadMore() }) { Text("Retry") }
                    }
                }
            }
            if (endReached) {
                item(key = "end") {
                    Text(
                        "That's all ${niches.size} niches",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}
