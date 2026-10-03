package com.rjbiermann.giffyviewer.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.ui.giffyFocus
import com.rjbiermann.giffyviewer.feature.feed.FeedFilterDialog
import com.rjbiermann.giffyviewer.feature.feed.FeedRepository
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import com.rjbiermann.giffyviewer.feature.feed.FeedViewModel
import com.rjbiermann.giffyviewer.feature.feed.packNicheRef
import com.rjbiermann.giffyviewer.feature.feed.title
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Pinned-feed screen (niche or creator — any FeedSource, grid + player).
 * Reuses the home GifCard look; MENU quick actions stay home-only for now.
 */
@Composable
fun TvSourceFeedScreen(
    source: FeedSource,
    onOpenGif: (List<Gif>, Int) -> Unit,
    viewModel: TvNicheFeedViewModel,
    /** Shared quick-action model (favorite/block/custom-feed adds). */
    feedViewModel: com.rjbiermann.giffyviewer.feature.feed.FeedViewModel,
    /** Show-more pane niche rows — the feed swaps (same semantics as onOpenCreator). */
    onOpenNiche: (com.rjbiermann.giffyviewer.feature.feed.FeedSource.Niche) -> Unit = {},
    /** Preview-on-focus (AGENTS-UX-PATTERNS): null factory = no previews. */
    playerFactory: com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory? = null,
    dataSaver: Boolean = false,
) {
    val gifs = remember(source.keyBase) { viewModel.gifs(source) }.collectAsLazyPagingItems()
    // Preview-on-focus (same FocusPreview shape as the home rows).
    val previewContext = androidx.compose.ui.platform.LocalContext.current
    val preview =
        remember(source, playerFactory) {
            if (playerFactory == null) {
                null
            } else {
                FocusPreview(playerFactory, previewContext)
            }
        }
    androidx.compose.runtime.DisposableEffect(preview) {
        onDispose { preview?.release() }
    }
    // MENU quick actions (mobile QuickBlockSheet parity) — any pinned feed.
    var actionsFor by remember { mutableStateOf<Gif?>(null) }
    // §8 per-feed filter (same feedprefs storage as mobile; the shared dialog
    // is M3 and D-pad-focusable). Writing prefs restarts the pager.
    val feedPrefs by feedViewModel
        .feedPrefs(source.baseKey)
        .collectAsState(
            initial =
                com.rjbiermann.giffyviewer.core.datastore
                    .FeedPrefs(),
        )
    var showFilter by remember { mutableStateOf(false) }
    // Initial D-pad focus (AGENTS-APP pattern): the grid's first card takes
    // focus on open — no blind first press.
    val firstCardFocus = remember { FocusRequester() }
    LaunchedEffect(gifs.itemCount > 0) {
        if (gifs.itemCount > 0) firstCardFocus.requestFocus()
    }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Title + creator stats (v1/users — site-profile counts, tiles-only feeds).
        val stats by feedViewModel.creatorStats.collectAsStateWithLifecycle()
        LaunchedEffect(source) {
            feedViewModel.refreshCreatorStats((source as? FeedSource.Creator)?.username)
        }
        Column {
            Text(
                text = source.title(),
                // F8 (batch 15): same slot as mobile's GiffyScaffold title.
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 0.dp),
            )
            if (source is FeedSource.Creator) {
                stats?.let { s ->
                    Text(
                        text =
                            "%,d posts · %,d followers · %,d views".format(
                                java.util.Locale.US,
                                s.gifs,
                                s.followers,
                                s.views,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                    )
                }
            }
        }
        // Focus ring (unified BrandRed, AGENTS-APP): M3 FilterChip's own focus
        // state is subtle on 10-foot — the shared giffyFocus border marks it.
        val chipInteraction =
            remember { MutableInteractionSource() }
        androidx.compose.material3.FilterChip(
            selected = showFilter,
            onClick = { showFilter = true },
            interactionSource = chipInteraction,
            label = { Text("Filter") },
            modifier =
                Modifier
                    .giffyFocus(chipInteraction, shape = RoundedCornerShape(8.dp))
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize().padding(top = 44.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(count = gifs.itemCount, key = { i -> gifs[i]?.id ?: "pending$i" }) { i ->
                gifs[i]?.let { gif ->
                    GifCard(
                        gif = gif,
                        modifier =
                            Modifier
                                .then(if (i == 0) Modifier.focusRequester(firstCardFocus) else Modifier)
                                .fillMaxWidth(),
                        onMenu = { actionsFor = gif },
                        preview = preview,
                        dataSaver = dataSaver,
                        onClick = { onOpenGif(snapshot(gifs), gifs.indexOf(gif.id)) },
                    )
                }
            }
        }
    }
    if (showFilter) {
        FeedFilterDialog(
            // §8 strict-tags chip: tag-bundle custom feeds only (the merged groups).
            isTagBundle =
                source is FeedSource.Custom &&
                    source.refs.isNotEmpty() &&
                    source.refs.all { !it.startsWith("creator:") && !it.startsWith("niche:") },
            prefs = feedPrefs,
            onApply = { next ->
                feedViewModel.setFeedPrefs(source.baseKey, next)
                showFilter = false
            },
            onDismiss = { showFilter = false },
        )
    }
    actionsFor?.let { gif ->
        TvQuickActionsDialog(
            gif = gif,
            feedViewModel = feedViewModel,
            addableFeedRef = (source as? FeedSource.Niche)?.let { packNicheRef(it.id, it.name, prefixed = true) },
            onDismiss = { actionsFor = null },
            onOpenNiche = onOpenNiche,
        )
    }
}

@HiltViewModel
class TvNicheFeedViewModel
    @Inject
    constructor(
        private val repository: FeedRepository,
    ) : ViewModel() {
        fun gifs(source: FeedSource) = repository.paging(source).cachedIn(viewModelScope)
    }
