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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import com.rjbiermann.giffyviewer.core.datastore.FeedPrefs
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.player.GiffyPlayerFactory
import com.rjbiermann.giffyviewer.core.ui.EmptyState
import com.rjbiermann.giffyviewer.core.ui.giffyFocus
import com.rjbiermann.giffyviewer.feature.feed.CreatorFeedHeader
import com.rjbiermann.giffyviewer.feature.feed.FeedErrorKind
import com.rjbiermann.giffyviewer.feature.feed.FeedFilterDialog
import com.rjbiermann.giffyviewer.feature.feed.FeedRepository
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import com.rjbiermann.giffyviewer.feature.feed.FeedViewModel
import com.rjbiermann.giffyviewer.feature.feed.NicheFeedHeader
import com.rjbiermann.giffyviewer.feature.feed.NicheJoinViewModel
import com.rjbiermann.giffyviewer.feature.feed.activeSort
import com.rjbiermann.giffyviewer.feature.feed.defaultSort
import com.rjbiermann.giffyviewer.feature.feed.feedErrorCard
import com.rjbiermann.giffyviewer.feature.feed.feedErrorKind
import com.rjbiermann.giffyviewer.feature.feed.joinNicheLabel
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

/** Issue #45: cards per horizontal band row (source feeds render home-page rows). */
internal const val FEED_ROW_CARDS = 4

/** Pure chunking (FeedRowsTest): pager item count → row bands of [FEED_ROW_CARDS]. */
internal fun feedRowCount(itemCount: Int): Int = if (itemCount <= 0) 0 else (itemCount + FEED_ROW_CARDS - 1) / FEED_ROW_CARDS

/** Cards in [row] for an item count (the last band is partial). */
internal fun feedRowCardCount(
    itemCount: Int,
    row: Int,
): Int = (itemCount - row * FEED_ROW_CARDS).coerceIn(0, FEED_ROW_CARDS)

/**
 * Pinned-feed screen (niche or creator — any FeedSource). Issue #45: the old
 * multi-column staggered grid became HOME-PAGE ROWS — vertical bands of
 * horizontally scrolling [GifCard]s (SourceFeedRow), so the TV row-size
 * setting (tv_row_height → rowHeightFor) applies here too. Niche feeds get
 * For You-style §8 filter chips (orientation per-feed + verified-only global);
 * everything else (Refresh/Sort icons, honest empty/error footer, quick
 * actions, preview-on-focus, focus restore) is unchanged.
 */
@Composable
fun TvSourceFeedScreen(
    source: FeedSource,
    onOpenGif: (List<Gif>, Int) -> Unit,
    viewModel: TvNicheFeedViewModel,
    /** Shared quick-action model (favorite/block/custom-feed adds). */
    feedViewModel: FeedViewModel,
    /** Issue #47 slice 1: niche-feed Join/Leave state; null (default) keeps
     *  the header info-only — non-niche hosts and anonymous users included. */
    joinViewModel: NicheJoinViewModel? = null,
    /** Show-more pane niche rows — the feed swaps (same semantics as onOpenCreator). */
    onOpenNiche: (FeedSource.Niche) -> Unit = {},
    /** #18 sort apply: the host re-identifies the feed with the sorted source
     *  (mobile's shared-VM open() path is unavailable on TV — the pager
     *  lives in [TvNicheFeedViewModel], which the screen itself forces). */
    onSortChange: (FeedSource) -> Unit = {},
    /** Preview-on-focus (AGENTS-UX-PATTERNS): null factory = no previews. */
    playerFactory: GiffyPlayerFactory? = null,
    dataSaver: Boolean = false,
    /** Issue #13 §4.3: saveable focus position — BACK into a covered entry
     *  must land the SAME card. -1 = fresh entry (first-card focus). */
    savedFocusItem: Int = -1,
    onFocusSaved: (Int) -> Unit = {},
) {
    val gifs = remember(source.keyBase) { viewModel.gifs(source) }.collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showFilter by remember { mutableStateOf(false) }
    // Row-size setting (slice 11): the SAME DataStore pref the home rows read.
    val tvRowHeightPref by viewModel.tvRowHeight.collectAsStateWithLifecycle("default")
    val rowHeight = rowHeightFor(tvRowHeightPref)
    // Preview-on-focus (same FocusPreview shape as the home rows).
    val previewContext = LocalContext.current
    val preview =
        remember(source, playerFactory) {
            if (playerFactory == null) {
                null
            } else {
                FocusPreview(playerFactory, previewContext)
            }
        }
    DisposableEffect(preview) {
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
        .collectAsState(initial = FeedPrefs())
    // F4 (TV e2e sweep): the honest-empty predicate needs the GLOBAL §6
    // orientation under the per-feed override, same merge as mobile FeedScreen.
    val globalOrientation by feedViewModel.orientationFilter.collectAsStateWithLifecycle("any")
    val verifiedOnlyPref by feedViewModel.verifiedOnly.collectAsStateWithLifecycle(false)
    // Issue #28: the §5 video-fit pref governs the row cards (ONE collect at
    // the parent; "fit" is the default and leaves the cards unchanged — TV
    // no-jump invariant).
    val videoFit by feedViewModel.videoFit.collectAsStateWithLifecycle("fit")
    // §6/#22 refresh: forced generation + row-band reset (fresh horizontal
    // offsets via the row keys) + focus re-anchored to card 0 (the row-0
    // restore latch grabs it because refreshKey > 0 forces target 0).
    var refreshKey by remember { mutableIntStateOf(0) }

    fun refreshAndReturnToTop() {
        viewModel.refresh()
        refreshKey += 1
        scope.launch { listState.scrollToItem(0) }
    }
    // §4.3: the card that carries the focus requester + restore latch. After
    // an in-session refresh the anchor is card 0; otherwise the saved card
    // (fresh entry: focusTargetFor falls back to 0 = first-card focus).
    val focusTarget = focusTargetFor(if (refreshKey > 0) 0 else savedFocusItem, gifs.itemCount)
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Title row + creator/niche headers (#31) with the Refresh /
            // Sort&filter icons BESIDE the title (F3, TV e2e sweep).
            LaunchedEffect(source) {
                feedViewModel.refreshCreatorStats((source as? FeedSource.Creator)?.username)
                // Issue #31: the header gate clears the niche stats for every
                // non-niche source (same null-clear call as the creator's).
                feedViewModel.refreshNicheStats(nicheSourceId(source))
                // Join/Leave (issue #47 slice 1): refresh membership on a
                // niche entry — GET /v2/niches/following, 1 request per open.
                if (joinViewModel != null && source is FeedSource.Niche) joinViewModel.refresh()
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
                // IconButton (§3.1, tinted via the scheme).
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
                // D-pad reachability (2026-10-17 device finding): directional
                // beams skip the header Join (it sits outside the chips row's
                // beam; UP landed on the far top-end icons). The chips row's
                // UP is redirected onto the Join (attached when logged in —
                // detached requesters are simply never addressed).
                val joinFocus = remember { FocusRequester() }
                // Issue #31: the niche feed previously rendered NO header at
                // all — same shared mount, under the title.
                feedViewModel.nicheStats.collectAsStateWithLifecycle().value?.let { detail ->
                    val joinedIds = joinViewModel?.joined?.collectAsStateWithLifecycle()?.value
                    val joinError = joinViewModel?.error?.collectAsStateWithLifecycle()?.value
                    val pendingIds = joinViewModel?.pending?.collectAsStateWithLifecycle()?.value
                    // D-pad reachability (2026-10-17 device finding): the plain
                    // traversal from the cards skipped the header Join button —
                    // the framework's directional beam jumps past it to the
                    // top-end icons (the Join sits rightward of the chips'
                    // beam). UP from the filter chips goes to the Join
                    // instead; UP again continues to the global header.
                    NicheFeedHeader(
                        niche = detail,
                        joinButton =
                            if (joinViewModel != null && joinViewModel.loggedIn) {
                                {
                                    NicheJoinButton(
                                        joined = detail.id in joinedIds!!,
                                        error = joinError,
                                        pending = detail.id in pendingIds!!,
                                        focusRequester = joinFocus,
                                        onToggle = { joinViewModel.toggle(detail.id) },
                                    )
                                }
                            } else {
                                null
                            },
                    )
                }
                // Issue #45: For You-style §8 chips where the feed supports
                // them — orientation (per-feed pref, same values the shared
                // FeedFilterDialog writes) + verified-creators-only (the
                // global §6 pref). Creator feeds: none. No new server params.
                NicheFilterChipsRow(
                    prefs = feedPrefs,
                    verifiedOnly = verifiedOnlyPref,
                    focusUp = joinFocus,
                    onOrientation = { value ->
                        feedViewModel.setFeedPrefs(source.baseKey, feedPrefs.copy(orientation = value))
                    },
                    onVerifiedOnly = { enabled ->
                        scope.launch { viewModel.setVerifiedOnly(enabled) }
                    },
                )
            }
            // #39 (TV e2e sweep 2026-10-16): the shared FeedTabStrip is NOT
            // mounted here — it is the mobile FeedScreen's main-tab chrome.
            // This screen keeps its own Refresh + Sort & filter affordances.
            // Issue #45: vertical body of horizontal card bands — the home
            // FeedRow shape (CenteredColumn/CenteredRow centering, rowHeight
            // band cards), fed from the ONE source pager chunked into rows.
            // Vertical scroll state is saveable per nav entry (BACK restores
            // position); the focus restore latch lives inside each row.
            CenteredColumn(deadband = 0.dp) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val rowCount = feedRowCount(gifs.itemCount)
                    items(count = rowCount, key = { r -> "row$r-$refreshKey" }) { r ->
                        SourceFeedRow(
                            gifs = gifs,
                            row = r,
                            focusTarget = focusTarget,
                            onOpenGif = onOpenGif,
                            onMenu = { gif, viaHold ->
                                actionsFor = gif
                                actionsViaHold = viaHold
                            },
                            preview = preview,
                            dataSaver = dataSaver,
                            videoFit = videoFit,
                            rowHeight = rowHeight,
                            onFocusSaved = onFocusSaved,
                        )
                    }
                    // End-of-feed footer (#22): keyed off load states — never a
                    // pager item, never inside snapshot(). F4 (TV e2e sweep
                    // 2026-10-16): honest contract for error / filtered-empty /
                    // settled-empty states; Refresh stays D-pad reachable.
                    val refreshState = gifs.loadState.refresh
                    val append = gifs.loadState.append as? LoadState.NotLoading
                    val filteredEmpty =
                        feedPrefs.duration.isNotEmpty() ||
                            feedPrefs.resolution.isNotEmpty() ||
                            feedPrefs.orientation.ifEmpty { globalOrientation } != "any" ||
                            feedPrefs.untaggedOnly ||
                            verifiedOnlyPref
                    if (gifs.itemCount == 0 && refreshState is LoadState.Error) {
                        // Issue #63: the honest failure kind (offline / rate limited /
                        // sign-in / upstream) instead of one generic sentence. The hint
                        // is TV copy — this slot retries with the D-pad Refresh button
                        // below, there is no tap/pull-down gesture on a 10-foot surface.
                        val kind = feedErrorKind(refreshState.error)
                        val errorCard = feedErrorCard(kind)
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                EmptyState(message = errorCard.message, hint = tvErrorHint(kind))
                                RefreshFooterButton(onClick = ::refreshAndReturnToTop)
                            }
                        }
                    } else if (gifs.itemCount == 0 && refreshState is LoadState.NotLoading && append?.endOfPaginationReached == true) {
                        item {
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
                        item {
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
    }
    if (showFilter) {
        // TV sort affordance (#18): the saved sort rides the SAME settings the
        // mobile VM persists; a CHANGED sort re-identifies the feed via the
        // host callback and replaces this nav entry, so the cached unsorted
        // pages don't serve the new order until the TTL lapses.
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
            // No chip chosen → the saved sort → the order this surface
            // actually fetches with (creator = latest, #65).
            selectedSort = source.activeSort.ifEmpty { savedSort.ifEmpty { source.defaultSort } },
            onApply = { next, sort ->
                feedViewModel.setFeedPrefs(source.baseKey, next)
                // Issue #13: the sort change REPLACES the entry via onSortChange
                // (host popUpTo replace); the old VM's refresh() is gone with it.
                // A fresh keyBase has its own cache — a re-applied sort inside the
                // shared TTL may serve the cached order (stale-while-revalidate).
                feedViewModel.setSort(source, sort)
                if (sort != source.activeSort) {
                    onSortChange(source.withSort(sort))
                }
                scope.launch { listState.scrollToItem(0) }
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

/**
 * Issue #45: ONE band row of the source feed — the home FeedRow shape:
 * [FEED_ROW_CARDS] band cards ([GifCard] with [rowHeight], width per gif
 * aspect via cardWidth) in a centered LazyRow. Focus restore (#13 §4.3):
 * when the screen's [focusTarget] card lives in this row, the row's latch
 * scrolls it into view and re-arms D-pad focus — once per entry appearance.
 */
@Composable
private fun SourceFeedRow(
    gifs: LazyPagingItems<Gif>,
    row: Int,
    focusTarget: Int,
    onOpenGif: (List<Gif>, Int) -> Unit,
    onMenu: (Gif, Boolean) -> Unit,
    preview: FocusPreview?,
    dataSaver: Boolean,
    videoFit: String,
    rowHeight: Dp,
    onFocusSaved: (Int) -> Unit,
) {
    val startIndex = row * FEED_ROW_CARDS
    val restored = focusTarget in startIndex until (startIndex + FEED_ROW_CARDS)
    val rowState = rememberLazyListState()
    // The latch is deliberately NOT saveable — it resets per re-entry, while
    // the saved index rides the host's saveables (home FeedRow parity).
    var focusRearmed by remember { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }
    LaunchedEffect(restored, gifs.itemCount) {
        if (!restored || focusRearmed || gifs.itemCount <= focusTarget) return@LaunchedEffect
        focusRearmed = true
        if (focusTarget > startIndex) rowState.scrollToItem(focusTarget - startIndex)
        runCatching { rowFocus.requestFocus() }
    }
    // Reserve the band's space while paging loads (home FeedRow parity — a
    // zero-height row that pops to full height shoves every row below).
    Column(modifier = Modifier.padding(vertical = 8.dp).heightIn(min = reservedHeight(rowHeight))) {
        CenteredRow(deadband = 0.dp) {
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(count = feedRowCardCount(gifs.itemCount, row), key = { c ->
                    gifs[startIndex + c]?.id ?: "pending${startIndex + c}"
                }) { c ->
                    val i = startIndex + c
                    gifs[i]?.let { gif ->
                        GifCard(
                            gif = gif,
                            modifier =
                                Modifier
                                    .width(cardWidth(gif, rowHeight))
                                    .then(if (i == focusTarget) Modifier.focusRequester(rowFocus) else Modifier),
                            onMenu = { viaHold -> onMenu(gif, viaHold) },
                            onFocusChange = { focused -> if (focused) onFocusSaved(i) },
                            onClick = { onOpenGif(snapshot(gifs), gifs.indexOf(gif.id)) },
                            preview = preview,
                            dataSaver = dataSaver,
                            videoFit = videoFit,
                            rowHeight = rowHeight,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Issue #45 niche-feed §8 chips (For You scope-row style): orientation chips
 * write the per-feed pref (SAME values the shared FeedFilterDialog persists —
 * "" = follow global), the verified-creators-only chip toggles the global §6
 * pref. Focus = giffyFocus ring (shared-module M3 chip exception, Focus.kt).
 */
@Composable
private fun NicheFilterChipsRow(
    prefs: FeedPrefs,
    verifiedOnly: Boolean,
    focusUp: FocusRequester? = null,
    onOrientation: (String) -> Unit,
    onVerifiedOnly: (Boolean) -> Unit,
) {
    Row(
        modifier =
            if (focusUp != null) {
                Modifier
                    .focusProperties { up = focusUp }
                    .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
            } else {
                Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            "Global" to "",
            "Any" to "any",
            "Vertical" to "vertical",
            "Horizontal" to "horizontal",
        ).forEach { (label, value) ->
            val interaction = remember { MutableInteractionSource() }
            FilterChip(
                selected = prefs.orientation == value,
                onClick = { onOrientation(value) },
                label = { Text(label) },
                interactionSource = interaction,
                modifier = Modifier.giffyFocus(interaction, shape = RoundedCornerShape(8.dp)),
            )
        }
        val verifiedInteraction = remember { MutableInteractionSource() }
        FilterChip(
            selected = verifiedOnly,
            onClick = { onVerifiedOnly(!verifiedOnly) },
            label = { Text("Verified creators only") },
            interactionSource = verifiedInteraction,
            modifier = Modifier.giffyFocus(verifiedInteraction, shape = RoundedCornerShape(8.dp)),
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TvNicheFeedViewModel
    @Inject
    constructor(
        private val repository: FeedRepository,
        private val settings: SettingsRepository,
    ) : ViewModel() {
        /** TV row-size setting (slice 11): default | large | xl | xxl — the
         *  SAME DataStore pref the home rows read, mapped by rowHeightFor(). */
        val tvRowHeight: Flow<String> = settings.tvRowHeight

        /** Issue #45 niche chip: the global verified-creators-only §6 pref. */
        fun setVerifiedOnly(enabled: Boolean) {
            viewModelScope.launch { settings.setVerifiedOnly(enabled) }
        }

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

/** Issue #47 slice 1: Join/Leave beside the niche-feed counts (logged-in only
 *  — the host gates on [NicheJoinViewModel].loggedIn). Same issue-#7
 *  tv-material focus tokens as the footer Refresh button; a failed write
 *  NEVER flips the label — the honest error lines under the button. */
@Composable
private fun NicheJoinButton(
    joined: Boolean,
    error: String?,
    pending: Boolean,
    focusRequester: FocusRequester? = null,
    onToggle: () -> Unit,
) {
    Column {
        val interaction = remember { MutableInteractionSource() }
        Button(
            onClick = onToggle,
            enabled = !pending,
            scale = ButtonScale.None,
            interactionSource = interaction,
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
            modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
        ) { TvText(joinNicheLabel(joined)) }
        if (error != null) {
            TvText(
                error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Issue #63: the shared card's touch hints ("tap", "pull down") don't exist on
 *  TV — this error slot always carries the D-pad [RefreshFooterButton] below it. */
private fun tvErrorHint(kind: FeedErrorKind): String =
    when (kind) {
        FeedErrorKind.Upstream -> "Server trouble — press Refresh."
        FeedErrorKind.Generic -> "Press Refresh to try again."
        else -> feedErrorCard(kind).hint
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
