package com.rjbiermann.giffyviewer.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 * Following screen (PLAN §7): followed creators + joined niches; entries jump
 * to the creator feed / niche feed. Heading mirrors the site's "Following".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowingScreen(
    onBack: () -> Unit,
    onOpenCreator: (String) -> Unit,
    onOpenNiche: (FeedSource.Niche) -> Unit,
    viewModel: FollowingViewModel,
) {
    val creators by viewModel.creators.collectAsStateWithLifecycle()
    val niches by viewModel.niches.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    // Scroll-to-top fab (#23): server-paged lists can run deep (count-100 per
    // request); same thresholds as the main feed, navigation-only.
    val listState = rememberLazyListState()
    val showFab by rememberScrollingUp(listState, SCROLL_TO_TOP_THRESHOLD)
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()

    GiffyScaffold(
        title = "Following",
        onBack = onBack,
    ) { padding ->
        if (viewModel.loadFailed.collectAsStateWithLifecycle().value) {
            com.rjbiermann.giffyviewer.core.ui.EmptyState(
                message = "Couldn't load your follows",
                hint = "Check your connection and retry",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            androidx.compose.material3.TextButton(
                onClick = { viewModel.refresh() },
                modifier = Modifier.padding(16.dp),
            ) { Text("Retry") }
            return@GiffyScaffold
        }
        if (creators.isEmpty() && niches.isEmpty()) {
            com.rjbiermann.giffyviewer.core.ui.EmptyState(
                message = "Nothing followed yet",
                hint = "Follow creators and join niches to fill this page",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@GiffyScaffold
        }
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
            ) {
                item(key = "creators-h") {
                    Text(
                        "Creators",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(creators, key = { "c" + it.username }) { creator ->
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
                item(key = "niches-h") {
                    Text(
                        "Niches",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(niches, key = { "n" + it.id }) { niche ->
                    ListItem(
                        headlineContent = { Text(niche.name ?: niche.id) },
                        supportingContent = { Text("${niche.gifs} gifs · ${niche.subscribers} members") },
                        modifier =
                            Modifier.clickable {
                                onOpenNiche(FeedSource.Niche(niche.id, niche.name ?: niche.id))
                            },
                    )
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
