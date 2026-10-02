package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rjbiermann.giffyviewer.core.ui.GiffyScaffold

/**
 * Niches browser (PLAN §7 groups groundwork): paginated taxonomy from
 * `v2/niches` (anonymous OK); tap opens the niche as a feed tab.
 * Site-parity 2026-10: scrollable category filter chips (`v2/niches/categories`)
 * and a Sort by menu (orders verified against the server's BadOrder message).
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
    var sortMenuOpen by remember { mutableStateOf(false) }

    GiffyScaffold(
        title = "Niches",
        onBack = onBack,
        actions = {
            androidx.compose.material3.TextButton(onClick = { sortMenuOpen = true }) {
                Text(
                    "Sort: ${sort.removeSuffix("_asc").removeSuffix("_desc").replaceFirstChar { it.uppercase() }}",
                )
            }
            androidx.compose.material3.DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false },
            ) {
                listOf(
                    "subscribers" to "Subscribers",
                    "posts" to "Gifs",
                    "alphabetical_asc" to "A–Z",
                    "alphabetical_desc" to "Z–A",
                    "random" to "Random",
                ).forEach { (value, label) ->
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
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
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
            items(niches.size, key = { niches[it].id }) { index ->
                val niche = niches[index]
                ListItem(
                    headlineContent = { Text(niche.name) },
                    supportingContent = { Text("${niche.gifs} gifs · ${niche.subscribers} subscribers") },
                    trailingContent = {
                        Row {
                            // §9 lingo: Join Niche / Leave Niche (logged-in only).
                            if (joinViewModel.loggedIn) {
                                TextButton(onClick = { joinViewModel.toggle(niche.id) }) {
                                    Text(if (niche.id in joined) "Leave" else "Join")
                                }
                            }
                            IconButton(onClick = { onOpenAbout(niche.id, niche.name) }) {
                                Icon(Icons.Outlined.Info, contentDescription = "about ${niche.name}")
                            }
                            IconButton(onClick = { viewModel.togglePin(niche) }) {
                                Icon(
                                    imageVector =
                                        if (pinned.any { it.startsWith("${niche.id}|") }) {
                                            Icons.Filled.PushPin
                                        } else {
                                            Icons.Outlined.PushPin
                                        },
                                    contentDescription = if (pinned.any { it.startsWith("${niche.id}|") }) "unpin" else "pin",
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable { onOpenNiche(FeedSource.Niche(niche.id, niche.name)) },
                )
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
    }
}
