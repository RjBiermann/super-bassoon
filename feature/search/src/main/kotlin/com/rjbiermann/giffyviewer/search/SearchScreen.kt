package com.rjbiermann.giffyviewer.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rjbiermann.giffyviewer.core.ui.CreatorLabel
import com.rjbiermann.giffyviewer.core.ui.avgColorOr
import com.rjbiermann.giffyviewer.feature.feed.FeedSource

/** Full-scope tab indices (GIFs · Images · Creators · Niches). */
private const val SCOPE_GIFS = 0

/** Top-range chips (ranged bare-search pools, live-verified 2026-10-15:
 *  week = top7, month = top28, all-time = top; pools differ per range). */
private val TOP_RANGES = listOf("Week" to "top7", "Month" to "top28", "All time" to "top")

/**
 * Search screen (PLAN §7): query field (borrowed 16dp-radius style) → recent
 * searches (tap re-runs, per-row remove + clear-all) → typed autocomplete rows
 * (suggestion text + gif count). Submit opens the results feed.
 *
 * Full-scope search (AGENTS-APP spec'd 2026-10-02): when a query is typed, a
 * GIFs · Images · Creators · Niches tab row appears — GIFs keeps the existing
 * submit path, the other scopes render inline (1 request on first open,
 * VM-cached; rows flow the ContentFilter).
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    viewModel: SearchViewModel,
    onOpenCreator: (String) -> Unit = {},
    onOpenNiche: (id: String, name: String) -> Unit = { _, _ -> },
    onOpenFeed: (FeedSource) -> Unit = {},
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldFocus = remember { FocusRequester() }

    fun submit(raw: String) {
        keyboard?.hide()
        viewModel.setQuery("")
        viewModel.submit(raw)?.let(onSubmit)
    }

    LaunchedEffect(Unit) { fieldFocus.requestFocus() }

    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).imePadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
            }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                placeholder = { Text("Search…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "clear query")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(onSearch = {
                        submit(query)
                    }),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
            )
        }

        // Hoisted reads (composable context): history-clear section + trending tags.
        val trendingTags = viewModel.trendingTags.collectAsStateWithLifecycle().value
        val images by viewModel.images.collectAsStateWithLifecycle()
        val creatorPreviews by viewModel.creatorPreviews.collectAsStateWithLifecycle()
        val nichePreviews by viewModel.nichePreviews.collectAsStateWithLifecycle()

        // Explorer sections (issue #11): the photos/niches strips fetch on first
        // collection (Lazily shared — first visibility of the screen's empty state).
        val topRanged by viewModel.topRanged.collectAsStateWithLifecycle()
        val trendingPhotos by viewModel.trendingPhotos.collectAsStateWithLifecycle()
        val trendingNiches by viewModel.trendingNiches.collectAsStateWithLifecycle()
        val exploreCreators by viewModel.exploreCreators.collectAsStateWithLifecycle()
        val exploreEndReached by viewModel.exploreEndReached.collectAsStateWithLifecycle(false)
        val exploreLoadFailed by viewModel.exploreLoadFailed.collectAsStateWithLifecycle(false)
        val matchedTags by viewModel.matchedTags.collectAsStateWithLifecycle()
        // Active Top-range chip: saveable so scrolling the empty state down and
        // back doesn't reset the strip (LazyColumn item state dies with composition).
        var topRange by rememberSaveable { mutableStateOf(TOP_RANGES.first().second) }
        LaunchedEffect(query.isBlank(), topRange) {
            if (query.isBlank()) viewModel.loadTopRanged(topRange)
        }

        // Full-scope tabs (spec'd 2026-10-02): visible while a query is typed.
        // GIFs = the existing submit path; other scopes render inline.
        var scope by remember { mutableIntStateOf(SCOPE_GIFS) }
        LaunchedEffect(scope, query) {
            if (query.isNotBlank()) {
                when (scope) {
                    0 -> viewModel.loadMatchedTags(query)
                    1 -> viewModel.loadScope(SearchViewModel.SCOPE_IMAGES, query)
                    2 -> viewModel.loadScope(SearchViewModel.SCOPE_CREATORS, query)
                    3 -> viewModel.loadScope(SearchViewModel.SCOPE_NICHES, query)
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Empty field → history only (no suggestions, no extra network — §7).
            if (query.isBlank()) {
                item {
                    TextButton(
                        onClick = { viewModel.clearHistory() },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) { Text("Clear search history") }
                }
            }
            items(history, key = { it.id }) { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                submit(entry.query)
                            }.padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = entry.query,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    IconButton(onClick = { viewModel.removeHistory(entry) }) {
                        Icon(Icons.Filled.Close, contentDescription = "remove ${entry.query}")
                    }
                }
            }
            items(suggestions, key = { "s:" + it.text }) { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                submit(suggestion.text)
                            }.padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = suggestion.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text(
                        text = "%,d gifs".format(suggestion.gifs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // --- Explorer sections (issue #11: the search page hosts the site's
            // empty-search content — ranged Top, photos, niches, creators — while
            // the query is empty, above "Trending tags"). Silent request failures
            // = absent sections, like the scope rows; VM-cached, never Room rows.
            if (query.isBlank()) {
                val topRows = topRanged[topRange].orEmpty()
                item(key = "top-gifs") {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Top gifs",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                            )
                            // Strip tiles have no player path (same rule as the
                            // Images tab) — the feed opens via this header.
                            TextButton(onClick = { onOpenFeed(FeedSource.TopRanged(topRange)) }) {
                                Text("Browse all →")
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            TOP_RANGES.forEach { (label, range) ->
                                FilterChip(
                                    selected = topRange == range,
                                    onClick = { topRange = range },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
                items(topRows.chunked(3), key = { "top:" + it.first().id }) { rowGifs ->
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        rowGifs.forEach { gif ->
                            PreviewThumb(
                                gif = gif,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .padding(2.dp),
                            )
                        }
                        repeat(3 - rowGifs.size) { Spacer(Modifier.weight(1f).padding(2.dp)) }
                    }
                }
            }
            if (query.isBlank() && trendingPhotos.isNotEmpty()) {
                item(key = "photos-header") {
                    Text(
                        "Trending photos",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(trendingPhotos.chunked(3), key = { "ph:" + it.first().id }) { rowGifs ->
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        rowGifs.forEach { gif ->
                            PreviewThumb(
                                gif = gif,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .padding(2.dp),
                            )
                        }
                        repeat(3 - rowGifs.size) { Spacer(Modifier.weight(1f).padding(2.dp)) }
                    }
                }
            }
            if (query.isBlank() && trendingNiches.isNotEmpty()) {
                item(key = "trending-niches") {
                    Column {
                        Text(
                            "Trending niches",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(trendingNiches, key = { it.id }) { niche ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                        Modifier
                                            .clip(MaterialTheme.shapes.small)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { onOpenNiche(niche.id, niche.name) }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Column {
                                        Text(niche.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            text = "%d subscribers".format(niche.subscribers),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Creators strip (moved from ExploreScreen): page 1 rides in on the
            // row's first composition; approaching the row's end fetches the next
            // page (the guards make repeated calls no-ops). Failed load = absent
            // section (no retry storms, same stance as the scope rows).
            if (query.isBlank() && !exploreLoadFailed) {
                item(key = "trending-creators") {
                    val rowState = rememberLazyListState()
                    LaunchedEffect(rowState) {
                        snapshotFlow { rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                            .collect { viewModel.loadMoreCreators() }
                    }
                    Column {
                        Text(
                            "Trending creators",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        LazyRow(
                            state = rowState,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(exploreCreators, key = { it.username }) { creator ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                        Modifier
                                            .width(240.dp)
                                            .clip(MaterialTheme.shapes.small)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { onOpenCreator(creator.username) }
                                            .padding(12.dp),
                                ) {
                                    AsyncImage(
                                        model = creator.profileImageUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(CircleShape),
                                    )
                                    Column(modifier = Modifier.padding(start = 12.dp)) {
                                        CreatorLabel(creator.username, creator.verified)
                                        Text(
                                            text = "%d followers · %d gifs".format(creator.followers, creator.gifs),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        if (exploreEndReached && exploreCreators.isNotEmpty()) {
                            Text(
                                "That's all ${exploreCreators.size} creators",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
            // Trending tags rows (hoisted read above; empty query only).
            if (query.isBlank() && trendingTags.isNotEmpty()) {
                item(key = "tags-header") {
                    Text(
                        "Trending tags",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(trendingTags, key = { "t:" + it.name }) { tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    submit(tag.name)
                                }.padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = "#${tag.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        Text(
                            text = "%,d gifs".format(tag.count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Full-scope tab row (site results-page parity: GIFs · Images ·
            // Creators · Niches) + the active scope's inline section.
            if (query.isNotBlank()) {
                item(key = "scope-tabs") {
                    TabRow(selectedTabIndex = scope) {
                        listOf("GIFs", "Images", "Creators", "Niches").forEachIndexed { i, label ->
                            Tab(selected = scope == i, onClick = { scope = i }, text = { Text(label) })
                        }
                    }
                }
                when (scope) {
                    // GIFs tab: matched tags (v1/tags/match) above the submit
                    // path — tap = the existing tag-feed path.
                    0 -> {
                        val tags = matchedTags[query.trim()].orEmpty()
                        if (tags.isNotEmpty()) {
                            item(key = "matched-tags") {
                                Column {
                                    Text(
                                        "Tags matching",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(tags) { tag ->
                                            FilterChip(
                                                selected = false,
                                                onClick = { submit(tag) },
                                                label = { Text("#$tag") },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        val imgs = images[query.trim()].orEmpty()
                        if (imgs.isEmpty()) {
                            item(key = "images-hint") {
                                SectionHint(if (images.containsKey(query.trim())) "No images found" else "")
                            }
                        } else {
                            items(imgs.chunked(3), key = { "img:" + it.first().id }) { rowGifs ->
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                    rowGifs.forEach { gif ->
                                        PreviewThumb(
                                            gif = gif,
                                            modifier =
                                                Modifier
                                                    .weight(1f)
                                                    .padding(2.dp),
                                        )
                                    }
                                    repeat(3 - rowGifs.size) { Spacer(Modifier.weight(1f).padding(2.dp)) }
                                }
                            }
                        }
                    }
                    2 -> {
                        val rows = creatorPreviews[query.trim()].orEmpty()
                        if (rows.isEmpty()) {
                            item(key = "creators-hint") {
                                SectionHint(if (creatorPreviews.containsKey(query.trim())) "No creators found" else "")
                            }
                        } else {
                            items(rows, key = { "cr:" + it.id }) { gif ->
                                PreviewRow(gif) {
                                    onOpenCreator(gif.userName)
                                }
                            }
                        }
                    }
                    3 -> {
                        val rows = nichePreviews[query.trim()].orEmpty()
                        if (rows.isEmpty()) {
                            item(key = "niches-hint") {
                                SectionHint(if (nichePreviews.containsKey(query.trim())) "No niches found" else "")
                            }
                        } else {
                            items(rows, key = { "ni:" + it.first.id }) { (niche, gif) ->
                                PreviewRow(gif, label = niche.name) {
                                    onOpenNiche(niche.id, niche.name)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Tab placeholders before their scope's request returns. */
@Composable
private fun SectionHint(text: String) {
    if (text.isNotEmpty()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

/** Static poster cell (Images scope: stills — no player path, no tap target). */
@Composable
private fun PreviewThumb(
    gif: com.rjbiermann.giffyviewer.core.model.Gif?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(
                    if (gif != null && gif.width > 0 && gif.height > 0) {
                        gif.width.toFloat() / gif.height
                    } else {
                        0.8f
                    },
                ).clip(RoundedCornerShape(8.dp))
                .background(
                    gif?.let { avgColorOr(it.avgColor, MaterialTheme.colorScheme.surfaceVariant) }
                        ?: MaterialTheme.colorScheme.surfaceVariant,
                ),
    ) {
        if (gif != null) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(gif.posterUrl ?: gif.sdUrl)
                        .crossfade(200)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Preview row (Creators/Niches scopes): thumbnail + label, whole row tappable. */
@Composable
private fun PreviewRow(
    gif: com.rjbiermann.giffyviewer.core.model.Gif?,
    label: String? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PreviewThumb(
            gif = gif,
            modifier = Modifier.size(width = 64.dp, height = 44.dp),
        )
        Text(
            text = label ?: "@${gif?.userName.orEmpty()}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
    }
}
