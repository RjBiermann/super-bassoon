package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.NicheDto
import kotlinx.coroutines.launch

/**
 * Niches browser (PLAN §7 groups groundwork): paginated taxonomy from
 * `v2/niches` (anonymous OK); tap opens the niche as a feed tab.
 * Site-parity 2026-10: scrollable category filter chips (`v2/niches/categories`)
 * and a Sort by menu (orders verified against the server's BadOrder message).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NichesScreen(
    onBack: () -> Unit,
    onOpenNiche: (FeedSource.Niche) -> Unit,
    onOpenAbout: (id: String, name: String) -> Unit,
    api: GifsApi,
    settings: SettingsRepository,
    joinViewModel: NicheJoinViewModel = hiltViewModel(),
) {
    val joined by joinViewModel.joined.collectAsStateWithLifecycle(initialValue = emptySet())
    LaunchedEffect(Unit) { joinViewModel.refresh() }
    var niches by remember { mutableStateOf<List<NicheDto>>(emptyList()) }
    val pinned by settings.pinnedNiches.collectAsStateWithLifecycle(initialValue = emptySet())
    var nextPage by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var sort by remember { mutableStateOf("subscribers") }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadMore() {
        if (loading || nextPage <= 0) return
        loading = true
        scope.launch {
            runCatching { api.niches(page = nextPage, category = category, order = sort) }
                .onSuccess { pageDto ->
                    loadFailed = false
                    niches = niches + pageDto.niches
                    nextPage = if (pageDto.page < pageDto.pages) pageDto.page + 1 else 0
                }.onFailure { loadFailed = true }
            loading = false
        }
    }
    LaunchedEffect(Unit) { loadMore() }
    LaunchedEffect(category, sort) {
        if (category == null && sort == "subscribers") return@LaunchedEffect // initial state
        nextPage = 1
        niches = emptyList()
        loadFailed = false
        loadMore()
    }
    LaunchedEffect(Unit) {
        runCatching { api.nicheCategories() }.onSuccess { categories = it.categories }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Niches") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                    sort = value
                                    sortMenuOpen = false
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            // Site-parity filter chips: All + one chip per niche category.
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = category == null,
                            onClick = { category = null },
                            label = { Text("All") },
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = if (category == cat) null else cat },
                            label = { Text(cat) },
                        )
                    }
                }
            }
            items(niches.size, key = { niches[it].id }) { index ->
                val niche = niches[index]
                val scope2 = rememberCoroutineScope()
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
                            IconButton(onClick = { scope2.launch { settings.togglePinnedNiche(niche.id, niche.name) } }) {
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
                        onClick = { loadMore() },
                        modifier = Modifier.padding(16.dp),
                    ) { Text("Retry") }
                }
            }
            if (nextPage > 0) {
                item(key = "load-more") {
                    TextButton(
                        onClick = { loadMore() },
                        modifier = Modifier.padding(16.dp),
                    ) { Text(if (loading) "Loading…" else "Load more") }
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
