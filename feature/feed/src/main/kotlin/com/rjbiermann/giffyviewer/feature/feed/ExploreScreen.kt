package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rjbiermann.giffyviewer.core.ui.CreatorLabel
import com.rjbiermann.giffyviewer.core.ui.GiffyScaffold
import com.rjbiermann.giffyviewer.core.ui.SCROLL_TO_TOP_THRESHOLD
import com.rjbiermann.giffyviewer.core.ui.ScrollToTopFab
import com.rjbiermann.giffyviewer.core.ui.rememberReducedMotion
import com.rjbiermann.giffyviewer.core.ui.rememberScrollingUp
import com.rjbiermann.giffyviewer.core.ui.scrollToTop
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
    viewModel: ExploreViewModel,
) {
    val creators by viewModel.creators.collectAsStateWithLifecycle()
    val endReached by viewModel.endReached.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()

    // Auto-fetch as the list walks toward the end (§9 UX note); a failed fetch
    // re-fires once loadFailed clears (Retry sets it false).
    LaunchedEffect(creators.size, loadFailed) {
        if (!loadFailed) viewModel.loadMore()
    }
    // Scroll-to-top fab (#23): deep-paginated creator list, same thresholds as
    // the main feed — no network action (Explore auto-fetches itself).
    val listState = rememberLazyListState()
    val showFab by rememberScrollingUp(listState, SCROLL_TO_TOP_THRESHOLD)
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()

    GiffyScaffold(
        title = "Explore",
        onBack = onBack,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
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
                            CreatorLabel(creator.username, creator.verified)
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
                            onClick = { viewModel.loadMore() },
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
