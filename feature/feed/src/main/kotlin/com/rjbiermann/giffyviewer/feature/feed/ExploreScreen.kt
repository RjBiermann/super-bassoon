package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rjbiermann.giffyviewer.core.network.CreatorSearchItemDto
import com.rjbiermann.giffyviewer.core.network.GifsApi
import kotlinx.coroutines.launch

/**
 * Explore screen (PLAN §9 lingo: site Explore = "Top Creators", /explore/creators,
 * verified 2026-10-01): paginated verified-creator list (anonymous OK); tap opens
 * the creator feed. End-of-list auto-fetch (§9 UX-gap note — no Load-more button).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onBack: () -> Unit,
    onOpenCreator: (String) -> Unit,
    api: GifsApi,
) {
    var creators by remember { mutableStateOf<List<CreatorSearchItemDto>>(emptyList()) }
    var nextPage by remember { mutableIntStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadMore() {
        if (loading || nextPage <= 0 || endReached) return
        loading = true
        val before = creators.size
        scope.launch {
            runCatching { api.verifiedCreators(page = nextPage) }
                .onSuccess { pageDto ->
                    // upstream pagination overlaps: page n re-lists page n-1 rows —
                    // dedup by username or the LazyColumn keys collide (crashed live).
                    creators = (creators + pageDto.creators).distinctBy { it.username }
                    nextPage++
                    // Server repeats the tail when exhausted and carries no page
                    // count — no growth means the list is complete.
                    if (creators.size == before) endReached = true
                }.onFailure { loading = false }
            loading = false
        }
    }

    // Initial load + auto-fetch as the list walks toward the end (§9 UX note).
    LaunchedEffect(creators.size) { loadMore() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explore") },
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
            itemsIndexed(creators, key = { _, c -> c.username }) { _, creator ->
                ListItem(
                    leadingContent = {
                        AsyncImage(
                            model = creator.profileImageUrl,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                        )
                    },
                    headlineContent = {
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("@${creator.username}")
                            if (creator.verified) {
                                com.rjbiermann.giffyviewer.core.ui.VerifiedTick(
                                    modifier = Modifier.padding(start = 4.dp).size(14.dp),
                                )
                            }
                        }
                    },
                    supportingContent = { Text("${creator.followers} followers · ${creator.gifs} gifs") },
                    modifier = Modifier.clickable { onOpenCreator(creator.username) },
                )
            }
            if (loadFailed) {
                item(key = "error") {
                    com.rjbiermann.giffyviewer.core.ui.EmptyState(
                        message = "Couldn't load creators",
                        hint = "Check your connection and retry",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                    TextButton(
                        onClick = { loadMore() },
                        modifier = Modifier.padding(16.dp),
                    ) { Text("Retry") }
                }
            }
            if (endReached) {
                item(key = "end") {
                    Text(
                        "That's all ${creators.size} creators",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
