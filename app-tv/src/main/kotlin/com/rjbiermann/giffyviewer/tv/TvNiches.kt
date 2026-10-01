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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.rjbiermann.giffyviewer.core.network.NicheDto
import com.rjbiermann.giffyviewer.core.network.upstreamApi
import com.rjbiermann.giffyviewer.core.ui.giffyFocus
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
        private val api: upstreamApi,
        val settings: SettingsRepository,
    ) : ViewModel() {
        private val _niches = MutableStateFlow<List<NicheDto>>(emptyList())
        val niches: StateFlow<List<NicheDto>> = _niches

        private val _endReached = MutableStateFlow(false)
        val endReached: StateFlow<Boolean> = _endReached

        private var nextPage = 1
        private var loading = false

        /** Fetches the next taxonomy page; safe to call repeatedly (D-pad focus walk). */
        fun loadMore() {
            if (loading || nextPage <= 0) return
            loading = true
            viewModelScope.launch {
                runCatching { api.niches(page = nextPage) }
                    .onSuccess { pageDto ->
                        _niches.value = _niches.value + pageDto.niches
                        nextPage = if (pageDto.page < pageDto.pages) pageDto.page + 1 else 0
                        _endReached.value = nextPage == 0
                    }
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
            modifier = Modifier.padding(24.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 24.dp),
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
                            }.padding(horizontal = 24.dp, vertical = 10.dp),
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
            if (endReached) {
                item(key = "end") {
                    Text(
                        "That's all ${niches.size} niches",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
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
) {
    val gifs = remember(source.keyBase) { viewModel.gifs(source) }.collectAsLazyPagingItems()
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Text(
            text = source.title(),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(24.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize().padding(top = 56.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(count = gifs.itemCount, key = { i -> gifs[i]?.id ?: "pending$i" }) { i ->
                gifs[i]?.let { gif ->
                    GifCard(
                        gif = gif,
                        modifier = Modifier.fillMaxWidth(),
                        onMenu = {},
                        onClick = { onOpenGif(snapshot(gifs), gifs.indexOf(gif.id)) },
                    )
                }
            }
        }
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
