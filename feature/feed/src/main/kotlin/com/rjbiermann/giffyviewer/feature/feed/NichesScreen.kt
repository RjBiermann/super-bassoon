package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.network.NicheDto
import com.rjbiermann.giffyviewer.core.network.upstreamApi
import kotlinx.coroutines.launch

/**
 * Niches browser (PLAN §7 groups groundwork): paginated taxonomy from
 * `v2/niches` (anonymous OK); tap opens the niche as a feed tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NichesScreen(
    onBack: () -> Unit,
    onOpenNiche: (FeedSource.Niche) -> Unit,
    api: upstreamApi,
    settings: SettingsRepository,
) {
    var niches by remember { mutableStateOf<List<NicheDto>>(emptyList()) }
    val pinned by settings.pinnedNiches.collectAsStateWithLifecycle(initialValue = emptySet())
    var nextPage by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadMore() {
        if (loading || nextPage <= 0) return
        loading = true
        scope.launch {
            runCatching { api.niches(page = nextPage) }
                .onSuccess { pageDto ->
                    niches = niches + pageDto.niches
                    nextPage = if (pageDto.page < pageDto.pages) pageDto.page + 1 else 0
                }
            loading = false
        }
    }
    remember {
        scope.launch { loadMore() }
        true
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
            items(niches.size, key = { niches[it].id }) { index ->
                val niche = niches[index]
                val scope2 = rememberCoroutineScope()
                ListItem(
                    headlineContent = { Text(niche.name) },
                    supportingContent = { Text("${niche.gifs} gifs · ${niche.subscribers} subscribers") },
                    trailingContent = {
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
                    },
                    modifier = Modifier.clickable { onOpenNiche(FeedSource.Niche(niche.id, niche.name)) },
                )
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
