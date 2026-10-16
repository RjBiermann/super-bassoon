package com.rjbiermann.giffyviewer.tv

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.ui.EmptyState
import com.rjbiermann.giffyviewer.feature.feed.CreatorFeedHeader
import com.rjbiermann.giffyviewer.feature.feed.FeedFilterDialog
import com.rjbiermann.giffyviewer.feature.feed.FeedRepository
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import com.rjbiermann.giffyviewer.feature.feed.FeedViewModel
import com.rjbiermann.giffyviewer.feature.feed.NicheFeedHeader
import com.rjbiermann.giffyviewer.feature.feed.activeSort
import com.rjbiermann.giffyviewer.feature.feed.nicheSourceId
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
import androidx.tv.material3.Text as TvText

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
    /** Issue #13 §4.2: per-entry saveable state (the Root hoist is replaced
     *  by the destination's rememberLazyStaggeredGridState()). */
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    /** Issue #13 §4.3: saveable grid focus position — BACK into a covered
     *  entry must land the SAME card (nav restores scroll; focus is
     *  composition-transient). -1 = fresh entry (first-card focus). */
    savedFocusItem: Int = -1,
    onFocusSaved: (Int) -> Unit = {},
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
    // F4 (TV e2e sweep): the honest-empty predicate needs the GLOBAL §6
    // orientation under the per-feed override, same merge as mobile FeedScreen.
    val globalOrientation by feedViewModel.orientationFilter.collectAsStateWithLifecycle("any")
    val verifiedOnlyPref by feedViewModel.verifiedOnly.collectAsStateWithLifecycle(false)
    // Issue #28: the §5 video-fit pref governs the grid tiles (ONE collect at
    // the parent; "fit" is the default and leaves the cards unchanged — TV
    // no-jump invariant).
    val videoFit by feedViewModel.videoFit.collectAsStateWithLifecycle("fit")
    // Initial D-pad focus (AGENTS-APP pattern): the grid's first card takes
    // focus on open — no blind first press. Issue #13 §4.3: the "first" card
    // is the SAVED focus on a re-entry (BACK from a player restores D-pad
    // focus, not just visual position) — one requester, one effect: on the
    // first content delivery, scroll to the target (no scroll for a fresh
    // entry at card 0) and grab focus. Keyed on the boolean so appends (item
    // count growth during a scroll session) can never re-grab focus.
    val firstCardFocus =
        remember { FocusRequester() }
    LaunchedEffect(gifs.itemCount > 0) {
        if (gifs.itemCount == 0) return@LaunchedEffect
        val target = focusTargetFor(savedFocusItem, gifs.itemCount)
        if (target > 0) gridState.scrollToItem(target)
        runCatching { firstCardFocus.requestFocus() }
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
            // Title row + creator/niche headers (#31) with the Refresh /
            // Sort&filter icons BESIDE the title — the old Box-overlay put
            // them on top of the title text (device pass: "Se [icon][icon]:
            // yoga"); the icons' right side hosts them now (title weight(1f)).
            LaunchedEffect(source) {
                feedViewModel.refreshCreatorStats((source as? FeedSource.Creator)?.username)
                // Issue #31: the header gate clears the niche stats for every
                // non-niche source (same null-clear call as the creator's).
                feedViewModel.refreshNicheStats(nicheSourceId(source))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = source.title(),
                    // F8 (batch 15): same slot as mobile's GiffyScaffold title.
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                )
                // Top-end icon row (§6 #22 + #18): Refresh (forced generation) and the
                // ONE Sort & filter entry (sort + filters + clear inside the shared
                // FeedFilterDialog). Focus = issue-#7 tokens on tv-material
                // IconButton (§3.1, tinted via the scheme); the old giffyFocus
                // ring is retired.
                Row(
                    modifier = Modifier.padding(end = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val refreshInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = { refreshAndReturnToTop() },
                        scale = ButtonScale.None,
                        interactionSource = refreshInteraction,
                        colors =
                            IconButtonDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        border =
                            IconButtonDefaults.border(
                                focusedBorder =
                                    Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary)),
                            ),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh feed")
                    }
                    val sortInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = { showFilter = true },
                        scale = ButtonScale.None,
                        interactionSource = sortInteraction,
                        colors =
                            IconButtonDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        border =
                            IconButtonDefaults.border(
                                focusedBorder =
                                    Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary)),
                            ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort & filter")
                    }
                }
            }
            // Creator/niche identity headers (#31) — full width, BELOW the
            // title/icon row.
            if (source is FeedSource.Creator) {
                feedViewModel.creatorStats.collectAsStateWithLifecycle().value?.let { s ->
                    // Issue #31: identity row (avatar + @user + bio)
                    // above the existing counts row (kept verbatim).
                    CreatorFeedHeader(s)
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
            if (source is FeedSource.Niche) {
                // Issue #31: the niche feed previously rendered NO header at
                // all — same shared mount, under the title.
                feedViewModel.nicheStats.collectAsStateWithLifecycle().value?.let { detail ->
                    NicheFeedHeader(detail)
                }
            }
            // #39 (TV e2e sweep 2026-10-16): the shared FeedTabStrip is NOT
            // mounted here — it is the mobile FeedScreen's main-tab chrome
            // (M3 FilterChips + DropdownMenu), reproduced as "TV was showing
            // mobile ui" on search results. TV navigation lives in its own
            // chrome: the home header pills (Settings/Niches/Search/More ▾),
            // quick-actions "Open feed", and the niche/creator seams below.
            // This screen keeps its own Refresh + Sort & filter affordances.
            // #15: staggered grid — true-aspect GifCard tiles (no square crop),
            // variable heights, same 4 columns / 16dp padding / 10dp spacing.
            // The state type changed with it (LazyStaggeredGridState) — hoisted
            // at Root and threaded; BACK-position parity lives there.
            // §4.3: the focus-requester target follows the saved card until the
            // user moves focus (then it rides the host's saveable). The
            // focus effect above consumes it once per content delivery.
            val focusTarget = focusTargetFor(savedFocusItem, gifs.itemCount)
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
                                    .then(if (i == focusTarget) Modifier.focusRequester(firstCardFocus) else Modifier)
                                    .fillMaxWidth(),
                            onFocusChange = { focused -> if (focused) onFocusSaved(i) },
                            onMenu = { viaHold ->
                                actionsFor = gif
                                actionsViaHold = viaHold
                            },
                            preview = preview,
                            dataSaver = dataSaver,
                            onClick = { onOpenGif(snapshot(gifs), gifs.indexOf(gif.id)) },
                            trueAspect = true,
                            videoFit = videoFit,
                        )
                    }
                }
                // End-of-feed footer (#22): composed AFTER the item slots, keyed off
                // load states — never a pager item, never inside snapshot(). The
                // button reuses the §6 refresh plumbing.
                // F4 (TV e2e sweep 2026-10-16): a FAILED refresh with zero cached
                // rows composed NOTHING — blank body, no error copy (the old
                // settled-only footer never rendered) — and a settled-empty feed
                // said "End of feed" over zero tiles. Same honest contract as
                // mobile FeedScreen; Refresh stays D-pad reachable in every state.
                val refreshState = gifs.loadState.refresh
                val append = gifs.loadState.append as? LoadState.NotLoading
                val filteredEmpty =
                    feedPrefs.duration.isNotEmpty() ||
                        feedPrefs.resolution.isNotEmpty() ||
                        feedPrefs.orientation.ifEmpty { globalOrientation } != "any" ||
                        feedPrefs.untaggedOnly ||
                        verifiedOnlyPref
                if (gifs.itemCount == 0 && refreshState is LoadState.Error) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            EmptyState(message = "Something went wrong", hint = "Retry the feed")
                            RefreshFooterButton(onClick = ::refreshAndReturnToTop)
                        }
                    }
                } else if (gifs.itemCount == 0 && refreshState is LoadState.NotLoading && append?.endOfPaginationReached == true) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            EmptyState(
                                message = if (filteredEmpty) "No videos match this filter" else "No videos here yet",
                                hint = if (filteredEmpty) "Clear or loosen the filters (Sort & filter above)" else null,
                            )
                            RefreshFooterButton(onClick = ::refreshAndReturnToTop)
                        }
                    }
                } else if (refreshState is LoadState.NotLoading && append != null && append.endOfPaginationReached) {
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
                            RefreshFooterButton(onClick = ::refreshAndReturnToTop)
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
                // Issue #13: the sort change REPLACES the entry via onSortChange
                // (host popUpTo replace); the old VM's refresh() is gone with it.
                // A fresh keyBase has its own cache — a re-applied sort inside the
                // shared TTL may serve the cached order (stale-while-revalidate;
                // the immediate-refetch the old refresh() gave applies when the
                // base is cold, as before).
                feedViewModel.setSort(source, sort)
                if (sort != source.activeSort) {
                    onSortChange(source.withSort(sort))
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

/** F4: the footer Refresh button (error / empty / end-of-feed states share it) —
 *  issue-#7 tv-material focus tokens, same look the old inline footer had. */
@Composable
private fun RefreshFooterButton(onClick: () -> Unit) {
    val endInteraction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        scale = ButtonScale.None,
        interactionSource = endInteraction,
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
        modifier = Modifier.padding(top = 4.dp),
    ) { TvText("Refresh") }
}
