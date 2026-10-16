package com.rjbiermann.giffyviewer.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Surface
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import com.rjbiermann.giffyviewer.feature.feed.NichesViewModel

/**
 * Niches browser for TV (PLAN §7 groundwork, mobile NichesScreen parity):
 * paginated taxonomy from `v2/niches` (anonymous OK). D-pad: Enter opens the
 * niche feed; MENU toggles the home-tab pin (shows in the top pill row).
 * State lives in the shared NichesViewModel (feature:feed).
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
    val pinned by viewModel.settings.pinnedNiches.collectAsStateWithLifecycle(initialValue = emptySet())

    // D-pad browsing has no tap-to-load-more: fetch as focus nears the end.
    LaunchedEffect(niches.size, endReached) {
        if (niches.isNotEmpty() && !endReached) viewModel.loadMore()
    }
    LaunchedEffect(Unit) { viewModel.loadMore() }

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
