package com.rjbiermann.giffyviewer.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.rjbiermann.giffyviewer.core.datastore.SettingsRepository
import com.rjbiermann.giffyviewer.core.model.Gif
import com.rjbiermann.giffyviewer.core.network.GifsApi
import com.rjbiermann.giffyviewer.core.network.NicheDto
import com.rjbiermann.giffyviewer.core.ui.giffyFocus
import com.rjbiermann.giffyviewer.feature.feed.FeedFilterDialog
import com.rjbiermann.giffyviewer.feature.feed.FeedRepository
import com.rjbiermann.giffyviewer.feature.feed.FeedSource
import com.rjbiermann.giffyviewer.feature.feed.title
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Niches browser for TV (PLAN §7 groundwork, mobile NichesScreen parity):
 * paginated taxonomy from `v2/niches` (anonymous OK). D-pad: Enter opens the
 * niche feed; MENU toggles the home-tab pin (shows in the top pill row).
 */
@HiltViewModel
class TvNichesViewModel
    @Inject
    constructor(
        private val api: GifsApi,
        val settings: SettingsRepository,
    ) : ViewModel() {
        private val _niches = MutableStateFlow<List<NicheDto>>(emptyList())
        val niches: StateFlow<List<NicheDto>> = _niches

        private val _endReached = MutableStateFlow(false)
        val endReached: StateFlow<Boolean> = _endReached

        private val _loadFailed = MutableStateFlow(false)
        val loadFailed: StateFlow<Boolean> = _loadFailed

        private var nextPage = 1
        private var loading = false

        /** Fetches the next taxonomy page; safe to call repeatedly (D-pad focus walk). */
        fun loadMore() {
            if (loading || nextPage <= 0) return
            loading = true
            viewModelScope.launch {
                runCatching { api.niches(page = nextPage) }
                    .onSuccess { pageDto ->
                        _loadFailed.value = false
                        _niches.value = _niches.value + pageDto.niches
                        nextPage = if (pageDto.page < pageDto.pages) pageDto.page + 1 else 0
                        _endReached.value = nextPage == 0
                    }.onFailure { _loadFailed.value = true }
                loading = false
            }
        }

        fun isPinned(
            niche: NicheDto,
            pinned: Set<String>,
        ) = pinned.any { it.startsWith("${niche.id}|") }

        fun togglePin(niche: NicheDto) {
            viewModelScope.launch { settings.togglePinnedNiche(niche.id, niche.name) }
        }
    }

@Composable
fun TvNichesScreen(
    onOpenNiche: (FeedSource.Niche) -> Unit,
    viewModel: TvNichesViewModel,
) {
    val niches by viewModel.niches.collectAsStateWithLifecycle()
    val endReached by viewModel.endReached.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    val pinned by viewModel.settings.pinnedNiches.collectAsStateWithLifecycle(initialValue = emptySet())

    // D-pad browsing has no tap-to-load-more: fetch as focus nears the end.
    LaunchedEffect(niches.size, endReached) {
        if (niches.isNotEmpty() && !endReached) viewModel.loadMore()
    }
    LaunchedEffect(Unit) { viewModel.loadMore() }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Niches",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            itemsIndexed(niches, key = { _, n -> n.id }) { index, niche ->
                val isPinned = pinned.any { it.startsWith("${niche.id}|") }
                val rowInteraction =
                    remember {
                        androidx.compose.foundation.interaction
                            .MutableInteractionSource()
                    }
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .giffyFocus(rowInteraction, fillOnFocus = MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(
                                interactionSource = rowInteraction,
                                indication = androidx.compose.material3.ripple(),
                            ) { onOpenNiche(FeedSource.Niche(niche.id, niche.name)) }
                            .onPreviewKeyEvent { event ->
                                // MENU toggles the home pin (creator-quick-action pattern).
                                if (event.type == KeyEventType.KeyUp && event.key == Key.Menu) {
                                    viewModel.togglePin(niche)
                                    true
                                } else {
                                    false
                                }
                            }.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = (if (isPinned) "📌 " else "") + niche.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${niche.gifs} gifs · ${niche.subscribers} subscribers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (loadFailed) {
                item(key = "error") {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        Text(
                            "Couldn't load niches — check your connection",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { viewModel.loadMore() }) { Text("Retry") }
                    }
                }
            }
            if (endReached) {
                item(key = "end") {
                    Text(
                        "That's all ${niches.size} niches",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

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
) {
    val gifs = remember(source.keyBase) { viewModel.gifs(source) }.collectAsLazyPagingItems()
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
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Text(
            text = source.title(),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        androidx.compose.material3.FilterChip(
            selected = showFilter,
            onClick = { showFilter = true },
            label = { Text("Filter") },
            modifier =
                Modifier
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
                        modifier = Modifier.fillMaxWidth(),
                        onMenu = { actionsFor = gif },
                        onClick = { onOpenGif(snapshot(gifs), gifs.indexOf(gif.id)) },
                    )
                }
            }
        }
    }
    if (showFilter) {
        FeedFilterDialog(
            isGroup = source is FeedSource.Group,
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
            addableFeedRef = (source as? FeedSource.Niche)?.let { "niche:${it.id}|${it.name}" },
            onDismiss = { actionsFor = null },
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
