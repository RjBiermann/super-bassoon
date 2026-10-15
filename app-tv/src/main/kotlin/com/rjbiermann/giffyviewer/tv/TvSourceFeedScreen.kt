package com.rjbiermann.giffyviewer.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.ui.giffyFocus
import com.rjbiermann.giffyviewer.feature.feed.FeedFilterDialog
import com.rjbiermann.giffyviewer.feature.feed.FeedRepository
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import com.rjbiermann.giffyviewer.feature.feed.FeedTabStrip
import com.rjbiermann.giffyviewer.feature.feed.FeedViewModel
import com.rjbiermann.giffyviewer.feature.feed.activeSort
import com.rjbiermann.giffyviewer.feature.feed.packNicheRef
import com.rjbiermann.giffyviewer.feature.feed.sortOptions
import com.rjbiermann.giffyviewer.feature.feed.title
import com.rjbiermann.giffyviewer.feature.feed.withSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
    /** #18 sort apply: the host re-identifies the feed with the sorted source
     *  (mobile's shared-VM open() path is unavailable on TV — the grid pager
     *  lives in [TvNicheFeedViewModel], which the screen itself forces). */
    onSortChange: (FeedSource) -> Unit = {},
    /** Preview-on-focus (AGENTS-UX-PATTERNS): null factory = no previews. */
    playerFactory: com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory? = null,
    dataSaver: Boolean = false,
    /** #19 tab strip: a tab opens that feed full-page (the openFeed path the
     *  issue names) — wired in TvMainActivity. */
    onSwitchFeed: (FeedSource) -> Unit = {},
    /** #19 strip seams to Root browser states (openFeed cleared so the browser
     *  branch composes); Explore/Following have no TV host today — hidden. */
    onOpenNiches: () -> Unit = {},
    onOpenCollections: () -> Unit = {},
    onOpenCustomFeeds: () -> Unit = {},
    /** Hoisted at TvMainActivity Root so BACK from the player keeps scroll. */
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
    val gifs = remember(source.keyBase) { viewModel.gifs(source) }.collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()
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
    // Issue #3: a HELD Center open must make the panel swallow its own
    // release KeyUp (MENU opens keep today's behavior).
    var actionsViaHold by remember { mutableStateOf(false) }
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
    val firstCardFocus =
        remember { FocusRequester() }
    LaunchedEffect(gifs.itemCount > 0) {
        if (gifs.itemCount > 0) firstCardFocus.requestFocus()
    }

    // §6/#22 refresh helper: forced generation + re-anchor to the top (instant
    // — TV lists reset) with the first card re-focused.
    fun refreshAndReturnToTop() {
        viewModel.refresh()
        scope.launch {
            gridState.scrollToItem(0)
            firstCardFocus.requestFocus()
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Title + creator stats (v1/users — site-profile counts, tiles-only
            // feeds) with the Refresh / Sort&filter icons aligned TopEnd above
            // them (same top region as before — the grid now flows BELOW the
            // #19 tab strip instead of clearing a fixed 44dp title band).
            LaunchedEffect(source) {
                feedViewModel.refreshCreatorStats((source as? FeedSource.Creator)?.username)
            }
            Box {
                Column {
                    val stats by feedViewModel.creatorStats.collectAsStateWithLifecycle()
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
                // Top-end icon row (§6 #22 + #18): Refresh (forced generation) and the
                // ONE Sort & filter entry (sort + filters + clear inside the shared
                // FeedFilterDialog). Focus ring (unified BrandRed, AGENTS-APP): M3
                // IconButton's own focus state is subtle on 10-foot — the shared
                // giffyFocus border marks it.
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val refreshInteraction = remember { MutableInteractionSource() }
                    androidx.compose.material3.IconButton(
                        onClick = { refreshAndReturnToTop() },
                        interactionSource = refreshInteraction,
                        modifier =
                            Modifier
                                .giffyFocus(refreshInteraction, shape = RoundedCornerShape(8.dp)),
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh feed",
                        )
                    }
                    val sortInteraction = remember { MutableInteractionSource() }
                    androidx.compose.material3.IconButton(
                        onClick = { showFilter = true },
                        interactionSource = sortInteraction,
                        modifier =
                            Modifier
                                .giffyFocus(sortInteraction, shape = RoundedCornerShape(8.dp)),
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort & filter",
                        )
                    }
                }
            }
            // #19: the shared tab strip — same fixed set as mobile (For You when
            // signed in only · Trending · Favorites · More ▾), hosted on EVERY TV
            // full feed exactly like mobile hosts its FeedScreen; a tab opens the
            // new source full-page via onSwitchFeed (Root openFeed). State inputs
            // are plain parameters — the strip keeps zero VM coupling.
            val isLoggedIn by feedViewModel.isLoggedIn.collectAsStateWithLifecycle(false)
            val pinnedNiches by feedViewModel.pinnedNiches.collectAsStateWithLifecycle(emptySet())
            val favoritedFeeds by feedViewModel.favoritedFeeds.collectAsStateWithLifecycle(emptyList())
            val pinnedCreators by feedViewModel.pinnedCreators.collectAsStateWithLifecycle(emptySet())
            val customFeeds by feedViewModel.customFeeds.collectAsStateWithLifecycle(emptyList())
            FeedTabStrip(
                source = source,
                isLoggedIn = isLoggedIn,
                pinnedNiches = pinnedNiches,
                favoritedFeeds = favoritedFeeds,
                pinnedCreators = pinnedCreators,
                customFeeds = customFeeds,
                onOpen = onSwitchFeed,
                // #25: the shared FeedViewModel outlives openFeed swaps (activity
                // scope) — the group-chip memory lives with the feed source itself.
                lastGroupSub = feedViewModel.lastGroupSub(),
                onOpenNiches = onOpenNiches,
                onOpenCollections = onOpenCollections,
                onOpenCustomFeeds = onOpenCustomFeeds,
            )
            // #15: staggered grid — true-aspect GifCard tiles (no square crop),
            // variable heights, same 4 columns / 16dp padding / 10dp spacing.
            // The state type changed with it (LazyStaggeredGridState) — hoisted
            // at Root and threaded; BACK-position parity lives there.
            LazyVerticalStaggeredGrid(
                state = gridState,
                columns = StaggeredGridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalItemSpacing = 10.dp,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(count = gifs.itemCount, key = { i -> gifs[i]?.id ?: "pending$i" }) { i ->
                    gifs[i]?.let { gif ->
                        GifCard(
                            gif = gif,
                            modifier =
                                Modifier
                                    .then(if (i == 0) Modifier.focusRequester(firstCardFocus) else Modifier)
                                    .fillMaxWidth(),
                            onMenu = { viaHold ->
                                actionsFor = gif
                                actionsViaHold = viaHold
                            },
                            preview = preview,
                            dataSaver = dataSaver,
                            onClick = { onOpenGif(snapshot(gifs), gifs.indexOf(gif.id)) },
                            trueAspect = true,
                        )
                    }
                }
                // End-of-feed footer (#22): composed AFTER the item slots, keyed off
                // load states — never a pager item, never inside snapshot(). The
                // button reuses the §6 refresh plumbing.
                val refreshSettled = gifs.loadState.refresh is LoadState.NotLoading
                val append = gifs.loadState.append as? LoadState.NotLoading
                if (refreshSettled && append != null && (append.endOfPaginationReached || gifs.itemCount == 0)) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "End of feed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val endInteraction = remember { MutableInteractionSource() }
                            TextButton(
                                onClick = { refreshAndReturnToTop() },
                                interactionSource = endInteraction,
                                modifier =
                                    Modifier
                                        .giffyFocus(endInteraction)
                                        .padding(top = 4.dp),
                            ) { Text("Refresh") }
                        }
                    }
                }
            }
        }
    }
    if (showFilter) {
        // TV sort affordance (#18): the saved sort rides the SAME settings the
        // mobile VM persists; a CHANGED sort re-identifies the feed via the
        // host callback and forces one generation (below), so the grid's cached
        // unsorted pages don't serve the new order until the TTL lapses.
        val savedSort by remember(source.baseKey) { feedViewModel.sortFor(source.baseKey) }
            .collectAsState(initial = "")
        FeedFilterDialog(
            // §8 strict-tags chip: tag-bundle custom feeds only (the merged groups).
            isTagBundle =
                source is FeedSource.Custom &&
                    source.refs.isNotEmpty() &&
                    source.refs.all { !it.startsWith("creator:") && !it.startsWith("niche:") },
            prefs = feedPrefs,
            sortOptions = source.sortOptions(),
            selectedSort = source.activeSort.ifEmpty { savedSort },
            onApply = { next, sort ->
                feedViewModel.setFeedPrefs(source.baseKey, next)
                feedViewModel.setSort(source, sort)
                if (sort != source.activeSort) {
                    onSortChange(source.withSort(sort))
                    viewModel.refresh()
                }
                scope.launch { gridState.scrollToItem(0) }
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
            onDismiss = {
                actionsFor = null
                actionsViaHold = false
            },
            openViaHold = actionsViaHold,
            onOpenNiche = onOpenNiche,
            onRefresh = { refreshAndReturnToTop() },
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TvNicheFeedViewModel
    @Inject
    constructor(
        private val repository: FeedRepository,
    ) : ViewModel() {
        /** Feed-refresh generation (§6): mirrors mobile's one-shot TTL bypass —
         *  the forced flag is consumed by the FIRST pager build of the
         *  generation, so a later rebuild in the same generation (source swap,
         *  preview re-attach) doesn't inherit it (stale-while-revalidate holds). */
        private val refreshGen = MutableStateFlow(0)
        private var consumedGen = -1

        /** The Refresh icon / "Refresh feed" quick-action row / end-of-feed
         *  footer: one forced generation for the open source. */
        fun refresh() {
            refreshGen.value = refreshGen.value + 1
        }

        /** The screen pins the flow to [source] via remember(source.keyBase);
         *  the combine keeps generation bumps live inside that flow. */
        fun gifs(source: FeedSource): Flow<PagingData<Gif>> =
            combine(flowOf(source), refreshGen) { src, gen -> src to gen }
                .flatMapLatest { (src, gen) ->
                    val forced = gen > 0 && consumedGen != gen
                    consumedGen = gen
                    repository.paging(src, forceRefresh = forced).cachedIn(viewModelScope)
                }
    }
